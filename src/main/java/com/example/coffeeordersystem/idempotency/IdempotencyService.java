package com.example.coffeeordersystem.idempotency;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Service
public class IdempotencyService {

  private final IdempotencyRepository idempotencyRepository;
  private final ObjectMapper objectMapper;

  public IdempotencyClaim claim(
      long userId,
      IdempotencyOperation operation,
      String idempotencyKey,
      String requestHash,
      Instant now) {
    idempotencyRepository.upsert(userId, operation, idempotencyKey, requestHash, now);
    IdempotencyRepository.IdempotencyRecord record =
        idempotencyRepository
            .findForUpdate(userId, operation, idempotencyKey)
            .orElseThrow(() -> new IllegalStateException("선점한 멱등 레코드를 찾을 수 없습니다."));
    JsonNode responseBody =
        record.responseBody() == null ? null : objectMapper.readTree(record.responseBody());
    return new IdempotencyClaim(
        record.id(), record.requestHash(), record.status(), record.httpStatus(), responseBody);
  }

  public void complete(
      long recordId,
      int httpStatus,
      String resultCode,
      JsonNode responseBody,
      Instant completedAt) {
    idempotencyRepository.complete(
        recordId, httpStatus, resultCode, responseBody.toString(), completedAt);
  }
}
