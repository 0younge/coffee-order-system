package com.example.coffeeordersystem.outbox.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutboxDeliveryClassifierTest {

  private final OutboxDeliveryClassifier classifier = new OutboxDeliveryClassifier();

  @Test
  @DisplayName("UT-OUTBOX-002 HTTP 상태를 성공·재시도·영구 실패로 분류한다")
  void classifiesHttpStatus() {
    assertTrue(classifier.classify(200).published());
    assertTrue(classifier.classify(299).published());

    assertFailure(301, false, OutboxErrorType.HTTP_3XX);
    assertFailure(307, false, OutboxErrorType.HTTP_3XX);
    assertFailure(400, false, OutboxErrorType.HTTP_4XX);
    assertFailure(408, true, OutboxErrorType.HTTP_4XX);
    assertFailure(429, true, OutboxErrorType.HTTP_4XX);
    assertFailure(499, false, OutboxErrorType.HTTP_4XX);
    assertFailure(500, true, OutboxErrorType.HTTP_5XX);
    assertFailure(599, true, OutboxErrorType.HTTP_5XX);
  }

  private void assertFailure(int status, boolean retryable, OutboxErrorType errorType) {
    OutboxDeliveryResult result = classifier.classify(status);
    assertFalse(result.published());
    assertEquals(retryable, result.retryable());
    assertEquals(errorType, result.errorType());
    assertEquals(status, result.httpStatus());
  }
}
