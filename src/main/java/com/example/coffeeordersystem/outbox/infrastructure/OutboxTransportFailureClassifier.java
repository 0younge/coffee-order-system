package com.example.coffeeordersystem.outbox.infrastructure;

import com.example.coffeeordersystem.outbox.domain.OutboxDeliveryResult;
import com.example.coffeeordersystem.outbox.domain.OutboxErrorType;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

class OutboxTransportFailureClassifier {

  OutboxDeliveryResult classify(Throwable throwable) {
    Throwable cause = unwrap(throwable);
    if (cause instanceof HttpTimeoutException) {
      return OutboxDeliveryResult.failed(true, OutboxErrorType.TIMEOUT, null);
    }
    return OutboxDeliveryResult.failed(true, OutboxErrorType.NETWORK, null);
  }

  private Throwable unwrap(Throwable throwable) {
    Throwable current = throwable;
    while ((current instanceof CompletionException || current instanceof ExecutionException)
        && current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }
}
