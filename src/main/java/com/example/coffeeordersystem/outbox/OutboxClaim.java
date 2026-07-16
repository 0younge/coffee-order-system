package com.example.coffeeordersystem.outbox;

record OutboxClaim(String eventId, String claimToken, String payload, int retryCount) {

  int attempt() {
    return retryCount + 1;
  }
}
