package com.example.coffeeordersystem.outbox;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class OutboxStore {

  private static final Duration LEASE = Duration.ofSeconds(30);

  private final JdbcTemplate jdbcTemplate;
  private final OutboxRetryPolicy retryPolicy = new OutboxRetryPolicy();

  OutboxStore(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
  public List<OutboxClaim> claim(Instant now, int limit) {
    if (limit <= 0) {
      throw new IllegalArgumentException("Outbox 배치 크기는 1 이상이어야 합니다.");
    }

    Timestamp dueAt = Timestamp.from(now);
    Timestamp leaseCutoff = Timestamp.from(now.minus(LEASE));
    List<OutboxClaim> claims = new ArrayList<>();
    Candidate cursor = null;
    while (claims.size() < limit) {
      List<Candidate> candidates =
          findCandidates(dueAt, leaseCutoff, cursor, limit - claims.size());
      if (candidates.isEmpty()) {
        break;
      }
      for (Candidate candidate : candidates) {
        lockAndClaim(candidate.eventId(), dueAt, leaseCutoff, now).ifPresent(claims::add);
      }
      cursor = candidates.get(candidates.size() - 1);
    }
    return List.copyOf(claims);
  }

  private List<Candidate> findCandidates(
      Timestamp dueAt, Timestamp leaseCutoff, Candidate cursor, int limit) {
    String baseSql =
        "SELECT event_id, next_retry_at, created_at FROM outbox_events "
            + "WHERE ((status = 'PENDING' AND next_retry_at <= ?) "
            + "OR (status = 'PROCESSING' AND locked_at <= ?)) ";
    if (cursor == null) {
      return jdbcTemplate.query(
          baseSql + "ORDER BY next_retry_at, created_at, event_id LIMIT ?",
          (resultSet, rowNumber) ->
              new Candidate(
                  resultSet.getString("event_id"),
                  resultSet.getTimestamp("next_retry_at"),
                  resultSet.getTimestamp("created_at")),
          dueAt,
          leaseCutoff,
          limit);
    }
    return jdbcTemplate.query(
        baseSql
            + "AND (next_retry_at, created_at, event_id) > (?, ?, ?) "
            + "ORDER BY next_retry_at, created_at, event_id LIMIT ?",
        (resultSet, rowNumber) ->
            new Candidate(
                resultSet.getString("event_id"),
                resultSet.getTimestamp("next_retry_at"),
                resultSet.getTimestamp("created_at")),
        dueAt,
        leaseCutoff,
        cursor.nextRetryAt(),
        cursor.createdAt(),
        cursor.eventId(),
        limit);
  }

  private Optional<OutboxClaim> lockAndClaim(
      String eventId, Timestamp dueAt, Timestamp leaseCutoff, Instant now) {
    Optional<LockedEvent> lockedEvent =
        jdbcTemplate
            .query(
                "SELECT payload, retry_count FROM outbox_events WHERE event_id = ? "
                    + "AND ((status = 'PENDING' AND next_retry_at <= ?) "
                    + "OR (status = 'PROCESSING' AND locked_at <= ?)) "
                    + "FOR UPDATE SKIP LOCKED",
                (resultSet, rowNumber) ->
                    new LockedEvent(
                        resultSet.getString("payload"), resultSet.getInt("retry_count")),
                eventId,
                dueAt,
                leaseCutoff)
            .stream()
            .findFirst();
    if (lockedEvent.isEmpty()) {
      return Optional.empty();
    }

    String claimToken = UUID.randomUUID().toString();
    int updated =
        jdbcTemplate.update(
            "UPDATE outbox_events SET status = 'PROCESSING', locked_at = ?, "
                + "claim_token = ? WHERE event_id = ?",
            Timestamp.from(now),
            claimToken,
            eventId);
    if (updated != 1) {
      throw new IllegalStateException("Outbox 이벤트를 선점할 수 없습니다.");
    }
    LockedEvent event = lockedEvent.orElseThrow();
    return Optional.of(new OutboxClaim(eventId, claimToken, event.payload(), event.retryCount()));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean publish(String eventId, String claimToken, Instant publishedAt) {
    int updated =
        jdbcTemplate.update(
            "UPDATE outbox_events SET status = 'PUBLISHED', next_retry_at = NULL, "
                + "locked_at = NULL, claim_token = NULL, published_at = ?, failed_at = NULL, "
                + "last_http_status = NULL, last_error_type = NULL "
                + "WHERE event_id = ? AND status = 'PROCESSING' AND claim_token = ?",
            Timestamp.from(publishedAt),
            eventId,
            claimToken);
    return updated == 1;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean fail(
      String eventId, String claimToken, OutboxDeliveryResult result, Instant failedAt) {
    Optional<Integer> currentRetryCount =
        jdbcTemplate
            .query(
                "SELECT retry_count FROM outbox_events "
                    + "WHERE event_id = ? AND status = 'PROCESSING' AND claim_token = ? "
                    + "FOR UPDATE",
                (resultSet, rowNumber) -> resultSet.getInt("retry_count"),
                eventId,
                claimToken)
            .stream()
            .findFirst();
    if (currentRetryCount.isEmpty()) {
      return false;
    }

    int retryCount = currentRetryCount.orElseThrow() + 1;
    Optional<Instant> nextRetryAt = retryPolicy.nextRetryAt(retryCount, failedAt);
    if (!result.retryable() || nextRetryAt.isEmpty()) {
      return markFailed(eventId, claimToken, retryCount, result, failedAt);
    }
    return scheduleRetry(eventId, claimToken, retryCount, result, nextRetryAt.orElseThrow());
  }

  private boolean scheduleRetry(
      String eventId,
      String claimToken,
      int retryCount,
      OutboxDeliveryResult result,
      Instant nextRetryAt) {
    int updated =
        jdbcTemplate.update(
            "UPDATE outbox_events SET status = 'PENDING', retry_count = ?, "
                + "next_retry_at = ?, locked_at = NULL, claim_token = NULL, "
                + "published_at = NULL, failed_at = NULL, last_http_status = ?, "
                + "last_error_type = ? WHERE event_id = ? AND status = 'PROCESSING' "
                + "AND claim_token = ?",
            retryCount,
            Timestamp.from(nextRetryAt),
            result.httpStatus(),
            result.errorType().name(),
            eventId,
            claimToken);
    return updated == 1;
  }

  private boolean markFailed(
      String eventId,
      String claimToken,
      int retryCount,
      OutboxDeliveryResult result,
      Instant failedAt) {
    int updated =
        jdbcTemplate.update(
            "UPDATE outbox_events SET status = 'FAILED', retry_count = ?, "
                + "next_retry_at = NULL, locked_at = NULL, claim_token = NULL, "
                + "published_at = NULL, failed_at = ?, last_http_status = ?, "
                + "last_error_type = ? WHERE event_id = ? AND status = 'PROCESSING' "
                + "AND claim_token = ?",
            retryCount,
            Timestamp.from(failedAt),
            result.httpStatus(),
            result.errorType().name(),
            eventId,
            claimToken);
    return updated == 1;
  }

  private record Candidate(String eventId, Timestamp nextRetryAt, Timestamp createdAt) {}

  private record LockedEvent(String payload, int retryCount) {}
}
