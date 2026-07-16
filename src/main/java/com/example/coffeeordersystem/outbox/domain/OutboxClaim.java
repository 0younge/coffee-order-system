package com.example.coffeeordersystem.outbox.domain;

public record OutboxClaim(String eventId, String claimToken, String payload, int retryCount) {

  public int attempt() {
    return retryCount + 1;
  }
}
