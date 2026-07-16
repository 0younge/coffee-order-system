package com.example.coffeeordersystem.outbox.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.coffeeordersystem.outbox.domain.OutboxDeliveryResult;
import com.example.coffeeordersystem.outbox.domain.OutboxErrorType;
import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutboxTransportFailureClassifierTest {

  private final OutboxTransportFailureClassifier classifier =
      new OutboxTransportFailureClassifier();

  @Test
  @DisplayName("UT-OUTBOX-002 timeout과 네트워크 오류를 응답 없는 재시도로 분류한다")
  void classifiesTransportFailure() {
    OutboxDeliveryResult timeout =
        classifier.classify(new CompletionException(new HttpTimeoutException("timeout")));
    OutboxDeliveryResult network = classifier.classify(new IOException("network"));

    assertFalse(timeout.published());
    assertTrue(timeout.retryable());
    assertEquals(OutboxErrorType.TIMEOUT, timeout.errorType());
    assertNull(timeout.httpStatus());
    assertFalse(network.published());
    assertTrue(network.retryable());
    assertEquals(OutboxErrorType.NETWORK, network.errorType());
    assertNull(network.httpStatus());
  }
}
