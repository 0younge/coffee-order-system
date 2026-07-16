package com.example.coffeeordersystem.order;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.coffeeordersystem.common.observability.BusinessEventLogger;
import com.example.coffeeordersystem.idempotency.IdempotencyClaim;
import com.example.coffeeordersystem.idempotency.IdempotencyOperation;
import com.example.coffeeordersystem.idempotency.IdempotencyService;
import com.example.coffeeordersystem.idempotency.RequestHasher;
import com.example.coffeeordersystem.menu.MenuResponse;
import com.example.coffeeordersystem.menu.MenuService;
import com.example.coffeeordersystem.outbox.OutboxEventWriter;
import com.example.coffeeordersystem.point.LockedPointBalance;
import com.example.coffeeordersystem.point.PointPaymentService;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class OrderServiceTest {

  @Test
  @DisplayName("CT-ORDER-001 사용자 락을 얻은 뒤 결제 시각을 확정한다")
  void capturesPaymentTimeAfterUserLock() throws Exception {
    PointPaymentService pointPaymentService = mock(PointPaymentService.class);
    IdempotencyService idempotencyService = mock(IdempotencyService.class);
    RequestHasher requestHasher = mock(RequestHasher.class);
    MenuService menuService = mock(MenuService.class);
    OrderRepository orderRepository = mock(OrderRepository.class);
    OutboxEventWriter outboxEventWriter = mock(OutboxEventWriter.class);
    ObjectMapper objectMapper = mock(ObjectMapper.class);
    Clock clock = mock(Clock.class);
    BusinessEventLogger businessEventLogger = mock(BusinessEventLogger.class);
    LockedPointBalance pointBalance = mock(LockedPointBalance.class);
    CountDownLatch lockAttempted = new CountDownLatch(1);
    CountDownLatch releaseLock = new CountDownLatch(1);
    Instant clockInstant = Instant.parse("2026-07-16T06:00:00.123456789Z");
    Instant paidAt = clockInstant.truncatedTo(ChronoUnit.MICROS);

    when(pointPaymentService.lock(1L))
        .thenAnswer(
            invocation -> {
              lockAttempted.countDown();
              assertTrue(releaseLock.await(2, TimeUnit.SECONDS));
              return pointBalance;
            });
    when(clock.instant()).thenReturn(clockInstant);
    when(requestHasher.hash(IdempotencyOperation.ORDER, 2L)).thenReturn("request-hash");
    when(idempotencyService.claim(
            1L, IdempotencyOperation.ORDER, "idempotency-key", "request-hash", paidAt))
        .thenReturn(new IdempotencyClaim(3L, "request-hash", "PROCESSING", null, null));
    when(menuService.findById(2L)).thenReturn(Optional.of(new MenuResponse(2L, "메뉴", 4_000L)));
    when(pointBalance.pay(4_000L, paidAt)).thenReturn(true);
    when(pointBalance.balance()).thenReturn(1_000L);
    Order savedOrder = mock(Order.class);
    when(savedOrder.id()).thenReturn(4L);
    when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
    when(outboxEventWriter.appendOrderPaid(4L, 1L, 2L, 4_000L, paidAt)).thenReturn("event-id");
    doReturn(mock(JsonNode.class)).when(objectMapper).valueToTree(any());

    OrderService orderService =
        new OrderService(
            pointPaymentService,
            idempotencyService,
            requestHasher,
            menuService,
            orderRepository,
            outboxEventWriter,
            objectMapper,
            clock,
            businessEventLogger);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      var result =
          executor.submit(() -> orderService.place(new OrderCommand(1L, 2L, "idempotency-key")));

      assertTrue(lockAttempted.await(2, TimeUnit.SECONDS));
      verifyNoInteractions(clock);
      releaseLock.countDown();
      result.get(2, TimeUnit.SECONDS);

      verify(clock).instant();
      verify(pointBalance).pay(4_000L, paidAt);
      verify(outboxEventWriter).appendOrderPaid(4L, 1L, 2L, 4_000L, paidAt);
    } finally {
      releaseLock.countDown();
      executor.shutdownNow();
    }
  }
}
