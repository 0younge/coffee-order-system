package com.example.coffeeordersystem.outbox.infrastructure;

import com.example.coffeeordersystem.outbox.application.OutboxDeliveryFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@ConditionalOnProperty(
    prefix = "outbox.worker",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
class OutboxWorker {

  private final OutboxDeliveryFacade outboxDeliveryFacade;
  private final OutboxWorkerSettings settings;

  @Scheduled(
      fixedDelayString = "#{@outboxWorkerSettings.pollIntervalMillis()}",
      initialDelayString = "#{@outboxWorkerSettings.pollIntervalMillis()}")
  void poll() {
    outboxDeliveryFacade.deliverDue(settings.batchSize());
  }
}
