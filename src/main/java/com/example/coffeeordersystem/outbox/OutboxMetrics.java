package com.example.coffeeordersystem.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class OutboxMetrics {

  private final JdbcTemplate jdbcTemplate;
  private final Clock clock;
  private final Counter published;
  private final Counter retried;
  private final Counter failed;
  private final Counter fencingRejected;

  OutboxMetrics(MeterRegistry meterRegistry, JdbcTemplate jdbcTemplate, Clock clock) {
    this.jdbcTemplate = jdbcTemplate;
    this.clock = clock;
    published = meterRegistry.counter("coffee.outbox.delivery.published");
    retried = meterRegistry.counter("coffee.outbox.delivery.retried");
    failed = meterRegistry.counter("coffee.outbox.delivery.failed");
    fencingRejected = meterRegistry.counter("coffee.outbox.fencing.rejected");
    Gauge.builder("coffee.outbox.pending", this, metrics -> metrics.count("PENDING"))
        .register(meterRegistry);
    Gauge.builder("coffee.outbox.failed", this, metrics -> metrics.count("FAILED"))
        .register(meterRegistry);
    Gauge.builder("coffee.outbox.oldest.pending.age", this, OutboxMetrics::oldestPendingAge)
        .baseUnit("seconds")
        .register(meterRegistry);
  }

  void published() {
    published.increment();
  }

  void retried() {
    retried.increment();
  }

  void failed() {
    failed.increment();
  }

  void fencingRejected() {
    fencingRejected.increment();
  }

  private double count(String status) {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM outbox_events WHERE status = ?", Long.class, status);
    return count == null ? 0 : count.doubleValue();
  }

  private double oldestPendingAge() {
    Timestamp oldest =
        jdbcTemplate.queryForObject(
            "SELECT MIN(created_at) FROM outbox_events WHERE status = 'PENDING'", Timestamp.class);
    if (oldest == null) {
      return 0;
    }
    Instant createdAt = oldest.toInstant();
    return Math.max(0, Duration.between(createdAt, clock.instant()).toMillis() / 1_000.0);
  }
}
