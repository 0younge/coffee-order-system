package com.example.coffeeordersystem.outbox.application;

import java.time.Instant;

public interface OutboxEventAppender {

  String appendOrderPaid(
      long orderId, long userId, long menuId, long paymentAmount, Instant occurredAt);
}
