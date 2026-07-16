package com.example.coffeeordersystem.outbox.domain;

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
}
