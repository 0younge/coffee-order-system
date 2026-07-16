package com.example.coffeeordersystem.outbox;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = "outbox.worker",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
class OutboxWorker {

  private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);

  private final OutboxStore outboxStore;
  private final OutboxHttpSender httpSender;
  private final Clock clock;
  private final OutboxMetrics metrics;
  private final int batchSize;
  private final AtomicBoolean active = new AtomicBoolean();

  OutboxWorker(
      OutboxStore outboxStore,
      OutboxHttpSender httpSender,
      Clock clock,
      OutboxMetrics metrics,
      @Value("${outbox.worker.batch-size:50}") int batchSize) {
    if (batchSize <= 0) {
      throw new IllegalArgumentException("Outbox 배치 크기는 1 이상이어야 합니다.");
    }
    this.outboxStore = outboxStore;
    this.httpSender = httpSender;
    this.clock = clock;
    this.metrics = metrics;
    this.batchSize = batchSize;
  }

  @Scheduled(
      fixedDelayString = "${outbox.worker.poll-interval-ms:1000}",
      initialDelayString = "${outbox.worker.poll-interval-ms:1000}")
  void poll() {
    if (!active.compareAndSet(false, true)) {
      return;
    }

    List<OutboxClaim> claims;
    try {
      claims = outboxStore.claim(clock.instant(), batchSize);
    } catch (RuntimeException exception) {
      active.set(false);
      log.atWarn()
          .addKeyValue("errorType", exception.getClass().getSimpleName())
          .log("Outbox 이벤트 선점에 실패했습니다.");
      return;
    }

    if (claims.isEmpty()) {
      active.set(false);
      return;
    }

    CompletableFuture<?>[] deliveries =
        claims.stream().map(this::deliver).toArray(CompletableFuture[]::new);
    CompletableFuture.allOf(deliveries)
        .whenComplete(
            (ignored, throwable) -> {
              if (throwable != null) {
                Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
                log.atWarn()
                    .addKeyValue("errorType", cause.getClass().getSimpleName())
                    .log("Outbox 배치 결과 반영 중 오류가 발생했습니다.");
              }
              active.set(false);
            });
  }

  private CompletableFuture<Void> deliver(OutboxClaim claim) {
    long startedAt = System.nanoTime();
    return httpSender
        .send(claim.payload())
        .thenAccept(
            result -> {
              String deliveryResult;
              if (result.published()) {
                boolean published =
                    outboxStore.publish(claim.eventId(), claim.claimToken(), clock.instant());
                if (published) {
                  metrics.published();
                  deliveryResult = "published";
                } else {
                  metrics.fencingRejected();
                  deliveryResult = "fencing_rejected";
                }
              } else {
                boolean failed =
                    outboxStore.fail(claim.eventId(), claim.claimToken(), result, clock.instant());
                if (!failed) {
                  metrics.fencingRejected();
                  deliveryResult = "fencing_rejected";
                } else if (result.retryable() && claim.attempt() < 4) {
                  metrics.retried();
                  deliveryResult = "retry_scheduled";
                } else {
                  metrics.failed();
                  deliveryResult = "failed";
                }
              }
              long latencyMillis =
                  java.time.Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
              log.atInfo()
                  .addKeyValue("eventId", claim.eventId())
                  .addKeyValue("attempt", claim.attempt())
                  .addKeyValue("target", "/events/orders")
                  .addKeyValue("latencyMs", latencyMillis)
                  .addKeyValue("result", deliveryResult)
                  .log("Outbox 이벤트 전달 완료");
            })
        .whenComplete(
            (ignored, throwable) -> {
              if (throwable != null) {
                Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
                log.atWarn()
                    .addKeyValue("eventId", claim.eventId())
                    .addKeyValue("attempt", claim.attempt())
                    .addKeyValue("result", "state_update_failed")
                    .addKeyValue("errorType", cause.getClass().getSimpleName())
                    .log("Outbox 이벤트 결과 반영 실패");
              }
            });
  }
}
