package com.example.coffeeordersystem.idempotency.application;

import com.example.coffeeordersystem.idempotency.infrastructure.IdempotencyRepository;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Service
public class IdempotencyFacade {

  private final IdempotencyRepository idempotencyRepository;

  public IdempotencyClaim claim(
      long userId,
      IdempotencyOperation operation,
      String idempotencyKey,
      String requestHash,
      Instant now) {
    String operationType = operation.name();
    idempotencyRepository.upsert(userId, operationType, idempotencyKey, requestHash, now);
    IdempotencyRepository.IdempotencyRecord record =
        idempotencyRepository
            .findForUpdate(userId, operationType, idempotencyKey)
            .orElseThrow(() -> new IllegalStateException("선점한 멱등 레코드를 찾을 수 없습니다."));
    return new IdempotencyClaim(
        record.id(),
        record.requestHash(),
        record.status(),
        record.httpStatus(),
        record.responseBody());
  }

  public void complete(
      long recordId, int httpStatus, String resultCode, String responseBody, Instant completedAt) {
    idempotencyRepository.complete(recordId, httpStatus, resultCode, responseBody, completedAt);
  }
}
