package com.example.coffeeordersystem.idempotency;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
class IdempotencyRepository {

  private final JdbcTemplate jdbcTemplate;

  void upsert(
      long userId,
      IdempotencyOperation operation,
      String idempotencyKey,
      String requestHash,
      Instant now) {
    Timestamp timestamp = Timestamp.from(now);
    jdbcTemplate.update(
        "INSERT INTO idempotency_records "
            + "(user_id, operation_type, idempotency_key, request_hash, status, "
            + "created_at, updated_at) VALUES (?, ?, ?, ?, 'PROCESSING', ?, ?) "
            + "ON DUPLICATE KEY UPDATE id = id",
        userId,
        operation.name(),
        idempotencyKey,
        requestHash,
        timestamp,
        timestamp);
  }

  Optional<IdempotencyRecord> findForUpdate(
      long userId, IdempotencyOperation operation, String idempotencyKey) {
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
                    resultSet.getString("response_body")),
            userId,
            operation.name(),
            idempotencyKey)
        .stream()
        .findFirst();
  }

  void complete(
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
  }

  record IdempotencyRecord(
      long id, String requestHash, String status, Integer httpStatus, String responseBody) {}
}
