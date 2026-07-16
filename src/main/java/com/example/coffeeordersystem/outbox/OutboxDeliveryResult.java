package com.example.coffeeordersystem.outbox;

record OutboxDeliveryResult(
    boolean published, boolean retryable, OutboxErrorType errorType, Integer httpStatus) {

  static OutboxDeliveryResult success() {
    return new OutboxDeliveryResult(true, false, null, null);
  }

  static OutboxDeliveryResult failed(
      boolean retryable, OutboxErrorType errorType, Integer httpStatus) {
    return new OutboxDeliveryResult(false, retryable, errorType, httpStatus);
  }
}
