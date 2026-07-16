package com.example.coffeeordersystem.idempotency.application;

public record IdempotencyClaim(
    long recordId, String requestHash, String status, Integer httpStatus, String responseBody) {

  public boolean completed() {
    return "COMPLETED".equals(status);
  }
}
