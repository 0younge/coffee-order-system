package com.example.coffeeordersystem.outbox.infrastructure;

import java.time.Instant;

record OrderPaidEventPayload(
    String eventId,
    String eventType,
    Instant occurredAt,
    long userId,
    long menuId,
    long paymentAmount) {}
