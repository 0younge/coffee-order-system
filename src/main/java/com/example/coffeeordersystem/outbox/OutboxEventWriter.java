package com.example.coffeeordersystem.outbox;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Component
public class OutboxEventWriter {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public String appendOrderPaid(
      long orderId, long userId, long menuId, long paymentAmount, Instant occurredAt) {
    String eventId = UUID.randomUUID().toString();
    String payload =
        objectMapper
            .valueToTree(
                new OrderPaidEventPayload(
                    eventId, "ORDER_PAID", occurredAt, userId, menuId, paymentAmount))
            .toString();
    Timestamp timestamp = Timestamp.from(occurredAt);
    jdbcTemplate.update(
        "INSERT INTO outbox_events "
            + "(event_id, order_id, event_type, payload, status, retry_count, "
            + "next_retry_at, created_at) VALUES (?, ?, 'ORDER_PAID', ?, 'PENDING', 0, ?, ?)",
        eventId,
        orderId,
        payload,
        timestamp,
        timestamp);
    return eventId;
  }
}
