package com.example.coffeeordersystem.outbox;

import java.sql.Statement;
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
    List<LockedEvent> lockedEvents =
        jdbcTemplate.query(
            "SELECT event_id, payload, retry_count FROM outbox_events "
                + "WHERE next_retry_at <= ? AND (status = 'PENDING' "
                + "OR (status = 'PROCESSING' AND locked_at <= ?)) "
                + "ORDER BY next_retry_at, created_at, event_id LIMIT ? FOR UPDATE SKIP LOCKED",
            (resultSet, rowNumber) ->
                new LockedEvent(
                    resultSet.getString("event_id"),
                    resultSet.getString("payload"),
                    resultSet.getInt("retry_count")),
            dueAt,
            leaseCutoff,
            limit);
    List<OutboxClaim> claims = new ArrayList<>();
    List<Object[]> batchArguments = new ArrayList<>();
    for (LockedEvent event : lockedEvents) {
      String claimToken = UUID.randomUUID().toString();
      claims.add(new OutboxClaim(event.eventId(), claimToken, event.payload(), event.retryCount()));
      batchArguments.add(new Object[] {Timestamp.from(now), claimToken, event.eventId()});
    }
    if (batchArguments.isEmpty()) {
      return List.of();
    }
    int[] updated =
        jdbcTemplate.batchUpdate(
            "UPDATE outbox_events SET status = 'PROCESSING', locked_at = ?, "
                + "claim_token = ? WHERE event_id = ?",
            batchArguments);
    for (int updateCount : updated) {
      if (updateCount != 1 && updateCount != Statement.SUCCESS_NO_INFO) {
        throw new IllegalStateException("Outbox 이벤트를 선점할 수 없습니다.");
      }
    }
    return List.copyOf(claims);
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

  private record LockedEvent(String eventId, String payload, int retryCount) {}
}
