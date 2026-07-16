package com.example.coffeeordersystem.outbox.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public class OutboxRetryPolicy {

  public Optional<Instant> nextRetryAt(int retryCount, Instant failedAt) {
    Duration delay =
        switch (retryCount) {
          case 1 -> Duration.ofMinutes(1);
          case 2 -> Duration.ofMinutes(5);
          case 3 -> Duration.ofMinutes(30);
          default -> null;
        };
    return delay == null ? Optional.empty() : Optional.of(failedAt.plus(delay));
  }
}
