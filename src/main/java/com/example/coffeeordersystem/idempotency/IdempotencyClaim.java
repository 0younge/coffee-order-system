package com.example.coffeeordersystem.idempotency;

import tools.jackson.databind.JsonNode;

public record IdempotencyClaim(
    long recordId, String requestHash, String status, Integer httpStatus, JsonNode responseBody) {

  public boolean completed() {
    return "COMPLETED".equals(status);
  }
}
