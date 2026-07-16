package com.example.coffeeordersystem.outbox.domain;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public class OutboxDeliveryClassifier {

  public OutboxDeliveryResult classify(int statusCode) {
    if (statusCode >= 200 && statusCode < 300) {
      return OutboxDeliveryResult.success();
    }
    if (statusCode >= 300 && statusCode < 400) {
      return OutboxDeliveryResult.failed(false, OutboxErrorType.HTTP_3XX, statusCode);
    }
    if (statusCode >= 400 && statusCode < 500) {
      boolean retryable = statusCode == 408 || statusCode == 429;
      return OutboxDeliveryResult.failed(retryable, OutboxErrorType.HTTP_4XX, statusCode);
    }
    return OutboxDeliveryResult.failed(true, OutboxErrorType.HTTP_5XX, statusCode);
  }

  public OutboxDeliveryResult classify(Throwable throwable) {
    Throwable cause = unwrap(throwable);
    if (cause instanceof HttpTimeoutException) {
      return OutboxDeliveryResult.failed(true, OutboxErrorType.TIMEOUT, null);
    }
    if (cause instanceof IOException) {
      return OutboxDeliveryResult.failed(true, OutboxErrorType.NETWORK, null);
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
