package com.example.coffeeordersystem.outbox;

import java.time.Instant;

public record OrderPaidEventPayload(
    String eventId,
    String eventType,
    Instant occurredAt,
    long userId,
    long menuId,
    long paymentAmount) {}
