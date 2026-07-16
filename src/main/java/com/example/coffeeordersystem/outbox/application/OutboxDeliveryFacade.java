package com.example.coffeeordersystem.outbox.application;

import com.example.coffeeordersystem.outbox.domain.OutboxClaim;
import com.example.coffeeordersystem.outbox.domain.OutboxDeliveryResult;
import com.example.coffeeordersystem.outbox.infrastructure.OutboxHttpSender;
import com.example.coffeeordersystem.outbox.infrastructure.OutboxMetrics;
import com.example.coffeeordersystem.outbox.infrastructure.OutboxStore;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Service
@ConditionalOnProperty(
    prefix = "outbox.worker",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class OutboxDeliveryFacade {

  private final OutboxStore outboxStore;
  private final OutboxHttpSender httpSender;
  private final Clock clock;
  private final OutboxMetrics metrics;
  private final AtomicBoolean active = new AtomicBoolean();

  public void deliverDue(int batchSize) {
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
                    .log("Outbox 배치 처리 중 오류가 발생했습니다.");
              }
              active.set(false);
            });
  }

  private CompletableFuture<Void> deliver(OutboxClaim claim) {
    long startedAt = System.nanoTime();
    CompletableFuture<OutboxDeliveryResult> delivery;
    try {
      delivery = httpSender.send(claim.payload());
    } catch (RuntimeException exception) {
      log.atWarn()
          .addKeyValue("eventId", claim.eventId())
          .addKeyValue("attempt", claim.attempt())
          .addKeyValue("target", "/events/orders")
          .addKeyValue(
              "latencyMs", java.time.Duration.ofNanos(System.nanoTime() - startedAt).toMillis())
          .addKeyValue("result", "delivery_start_failed")
          .addKeyValue("errorType", exception.getClass().getSimpleName())
          .log("Outbox 이벤트 전달 시작 실패");
      return CompletableFuture.failedFuture(exception);
    }
    return delivery
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
                    .addKeyValue("target", "/events/orders")
                    .addKeyValue(
                        "latencyMs",
                        java.time.Duration.ofNanos(System.nanoTime() - startedAt).toMillis())
                    .addKeyValue("result", "state_update_failed")
                    .addKeyValue("errorType", cause.getClass().getSimpleName())
                    .log("Outbox 이벤트 결과 반영 실패");
              }
            });
  }
}
