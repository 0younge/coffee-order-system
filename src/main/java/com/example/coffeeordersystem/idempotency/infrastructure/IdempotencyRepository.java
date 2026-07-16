package com.example.coffeeordersystem.idempotency.infrastructure;

import com.example.coffeeordersystem.common.api.ApiResponseJsonCodec;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Repository
public class IdempotencyRepository {

  private final JdbcTemplate jdbcTemplate;
  private final ApiResponseJsonCodec responseJsonCodec;

  public void upsert(
      long userId, String operationType, String idempotencyKey, String requestHash, Instant now) {
    Timestamp timestamp = Timestamp.from(now);
    jdbcTemplate.update(
        "INSERT INTO idempotency_records "
            + "(user_id, operation_type, idempotency_key, request_hash, status, "
            + "created_at, updated_at) VALUES (?, ?, ?, ?, 'PROCESSING', ?, ?) "
            + "ON DUPLICATE KEY UPDATE id = id",
        userId,
        operationType,
        idempotencyKey,
        requestHash,
        timestamp,
        timestamp);
  }

  public Optional<IdempotencyRecord> findForUpdate(
      long userId, String operationType, String idempotencyKey) {
    return jdbcTemplate
        .query(
            "SELECT id, request_hash, status, http_status, response_body "
                + "FROM idempotency_records "
                + "WHERE user_id = ? AND operation_type = ? AND idempotency_key = ? "
                + "FOR UPDATE",
            (resultSet, rowNumber) ->
                new IdempotencyRecord(
                    resultSet.getLong("id"),
                    resultSet.getString("request_hash"),
                    resultSet.getString("status"),
                    (Integer) resultSet.getObject("http_status"),
                    compact(resultSet.getString("response_body"))),
            userId,
            operationType,
            idempotencyKey)
        .stream()
        .findFirst();
  }

  public String complete(
      long recordId, int httpStatus, String resultCode, String responseBody, Instant completedAt) {
    Timestamp timestamp = Timestamp.from(completedAt);
    int updated =
        jdbcTemplate.update(
            "UPDATE idempotency_records SET status = 'COMPLETED', http_status = ?, "
                + "result_code = ?, response_body = ?, completed_at = ?, updated_at = ? "
                + "WHERE id = ? AND status = 'PROCESSING'",
            httpStatus,
            resultCode,
            responseBody,
            timestamp,
            timestamp,
            recordId);
    if (updated != 1) {
      throw new IllegalStateException("처리 중인 멱등 레코드를 완료할 수 없습니다.");
    }
    String storedResponseBody =
        jdbcTemplate.queryForObject(
            "SELECT response_body FROM idempotency_records WHERE id = ?", String.class, recordId);
    return compact(storedResponseBody);
  }

  private String compact(String responseBody) {
    return responseBody == null ? null : responseJsonCodec.compact(responseBody);
  }

  public record IdempotencyRecord(
      long id, String requestHash, String status, Integer httpStatus, String responseBody) {}
}
