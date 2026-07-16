package com.example.coffeeordersystem.idempotency;

import tools.jackson.databind.JsonNode;

public record IdempotencyClaim(
    long recordId,
    String requestHash,
    String status,
    Integer httpStatus,
    String resultCode,
    JsonNode responseBody) {

  public boolean completed() {
    return "COMPLETED".equals(status);
  }
}
