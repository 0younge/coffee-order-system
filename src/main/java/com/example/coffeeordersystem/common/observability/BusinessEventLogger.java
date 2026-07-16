package com.example.coffeeordersystem.common.observability;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
public class BusinessEventLogger {

  public void pointResult(long userId, String resultCode) {
    String requestId = RequestCorrelation.requestId();
    afterCommit(
        () ->
            log.atInfo()
                .addKeyValue("requestId", requestId)
                .addKeyValue("userId", userId)
                .addKeyValue("result", resultCode)
                .log("포인트 처리 완료"));
  }

  public void orderPaid(long userId, long orderId, String eventId) {
    String requestId = RequestCorrelation.requestId();
    afterCommit(
        () ->
            log.atInfo()
                .addKeyValue("requestId", requestId)
                .addKeyValue("userId", userId)
                .addKeyValue("orderId", orderId)
                .addKeyValue("eventId", eventId)
                .addKeyValue("result", "ORDER_PAID")
                .log("주문 처리 완료"));
  }

  private void afterCommit(Runnable action) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      action.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            action.run();
          }
        });
  }
}
