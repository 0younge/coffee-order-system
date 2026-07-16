package com.example.coffeeordersystem.outbox.domain;

public record OutboxDeliveryResult(
    boolean published, boolean retryable, OutboxErrorType errorType, Integer httpStatus) {

  public static OutboxDeliveryResult success() {
    return new OutboxDeliveryResult(true, false, null, null);
  }

  public static OutboxDeliveryResult failed(
      boolean retryable, OutboxErrorType errorType, Integer httpStatus) {
    return new OutboxDeliveryResult(false, retryable, errorType, httpStatus);
  }
}
