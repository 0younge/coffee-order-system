package com.example.coffeeordersystem.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class OrderConcurrencyTest {

  private static final AtomicLong ID_SEQUENCE = new AtomicLong(8_700_000_000L);

  @Autowired private OrderService orderService;

  @Autowired private JdbcTemplate jdbcTemplate;

  private long userId;
  private long menuId;

  @BeforeEach
  void setUp() {
    userId = ID_SEQUENCE.addAndGet(2);
    menuId = userId + 1;
    Timestamp now = Timestamp.from(Instant.parse("2026-07-16T00:00:00Z"));
    jdbcTemplate.update(
        "INSERT INTO users (id, point_balance, created_at, updated_at) VALUES (?, 5000, ?, ?)",
        userId,
        now,
        now);
    jdbcTemplate.update(
        "INSERT INTO menus (id, name, price, created_at) VALUES (?, '동시성 메뉴', 4000, ?)",
        menuId,
        now);
  }

  @AfterEach
  void tearDown() {
    jdbcTemplate.update(
        "DELETE FROM outbox_events WHERE order_id IN "
            + "(SELECT id FROM orders WHERE user_id = ?)",
        userId);
    jdbcTemplate.update("DELETE FROM orders WHERE user_id = ?", userId);
    jdbcTemplate.update("DELETE FROM idempotency_records WHERE user_id = ?", userId);
    jdbcTemplate.update("DELETE FROM menus WHERE id IN (?, ?)", menuId, menuId + 10_000L);
    jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
  }

  @Test
  @DisplayName("CT-ORDER-001 서로 다른 동시 주문은 잔액 범위 안에서 하나만 결제한다")
  void paysOnlyOneOfConcurrentDifferentOrders() throws Exception {
    List<OrderResult> results =
        executeConcurrently(
            List.of(command(UUID.randomUUID().toString()), command(UUID.randomUUID().toString())));

    Set<String> resultCodes =
        results.stream()
            .map(result -> result.body().get("code").stringValue())
            .collect(Collectors.toSet());
    assertEquals(Set.of("ORDER_PAID", "INSUFFICIENT_POINT"), resultCodes);
    assertEquals(1_000L, balance());
    assertEquals(1L, orderCount());
    assertEquals(1L, outboxCount());
    assertEquals(2L, idempotencyCount());
  }

  @Test
  @DisplayName("CT-IDEM-001 같은 주문 키가 동시에 도착해도 한 번만 결제한다")
  void paysConcurrentSameIdempotentOrderOnce() throws Exception {
    String key = UUID.randomUUID().toString();
    List<OrderCommand> commands = new ArrayList<>();
    for (int index = 0; index < 8; index++) {
      commands.add(command(key));
    }

    List<OrderResult> results = executeConcurrently(commands);

    assertTrue(results.stream().allMatch(result -> result.httpStatus() == 201));
    assertTrue(
        results.stream()
            .allMatch(result -> "ORDER_PAID".equals(result.body().get("code").stringValue())));
    assertEquals(
        1L,
        results.stream()
            .map(result -> result.body().get("data").get("orderId").longValue())
            .distinct()
            .count());
    assertEquals(1_000L, balance());
    assertEquals(1L, orderCount());
    assertEquals(1L, outboxCount());
    assertEquals(1L, idempotencyCount());
  }

  @Test
  @DisplayName("CT-IDEM-002 같은 키의 다른 메뉴가 동시에 도착하면 한 주문만 결제한다")
  void paysOnlyOneOfConcurrentDifferentMenusWithSameKey() throws Exception {
    long otherMenuId = menuId + 10_000L;
    jdbcTemplate.update(
        "INSERT INTO menus (id, name, price, created_at) VALUES (?, '다른 동시성 메뉴', 4000, ?)",
        otherMenuId,
        Timestamp.from(Instant.parse("2026-07-16T00:00:00Z")));
    String key = UUID.randomUUID().toString();

    List<OrderResult> results =
        executeConcurrently(List.of(command(menuId, key), command(otherMenuId, key)));

    Set<String> resultCodes =
        results.stream()
            .map(result -> result.body().get("code").stringValue())
            .collect(Collectors.toSet());
    assertEquals(Set.of("ORDER_PAID", "IDEMPOTENCY_KEY_REUSED"), resultCodes);
    assertEquals(1_000L, balance());
    assertEquals(1L, orderCount());
    assertEquals(1L, outboxCount());
    assertEquals(1L, idempotencyCount());
  }

  private List<OrderResult> executeConcurrently(List<OrderCommand> commands) throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(commands.size());
    try {
      List<Future<OrderResult>> requests =
          commands.stream()
              .map(
                  command ->
                      executor.submit(
                          () -> {
                            assertTrue(start.await(2, TimeUnit.SECONDS));
                            return orderService.place(command);
                          }))
              .toList();
      start.countDown();

      List<OrderResult> results = new ArrayList<>();
      for (Future<OrderResult> request : requests) {
        results.add(request.get(10, TimeUnit.SECONDS));
      }
      return results;
    } finally {
      executor.shutdownNow();
    }
  }

  private OrderCommand command(String idempotencyKey) {
    return command(menuId, idempotencyKey);
  }

  private OrderCommand command(long targetMenuId, String idempotencyKey) {
    return OrderCommand.from(new OrderRequest(userId, targetMenuId), idempotencyKey);
  }

  private long balance() {
    return count("SELECT point_balance FROM users WHERE id = " + userId);
  }

  private long orderCount() {
    return count("SELECT COUNT(*) FROM orders WHERE user_id = " + userId);
  }

  private long outboxCount() {
    return count(
        "SELECT COUNT(*) FROM outbox_events WHERE order_id IN "
            + "(SELECT id FROM orders WHERE user_id = "
            + userId
            + ")");
  }

  private long idempotencyCount() {
    return count("SELECT COUNT(*) FROM idempotency_records WHERE user_id = " + userId);
  }

  private long count(String sql) {
    Long value = jdbcTemplate.queryForObject(sql, Long.class);
    return value == null ? 0L : value;
  }
}
