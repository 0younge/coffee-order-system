package com.example.coffeeordersystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class MultiInstanceApiTest {

  private static final AtomicLong ID_SEQUENCE = new AtomicLong(9_600_000_000L);

  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

  private ConfigurableApplicationContext firstContext;
  private ConfigurableApplicationContext secondContext;
  private long userId;
  private long menuId;

  @AfterEach
  void tearDown() {
    try {
      if (firstContext != null && firstContext.isActive() && userId != 0) {
        JdbcTemplate jdbcTemplate = firstContext.getBean(JdbcTemplate.class);
        jdbcTemplate.update(
            "DELETE FROM outbox_events WHERE order_id IN "
                + "(SELECT id FROM orders WHERE user_id = ?)",
            userId);
        jdbcTemplate.update("DELETE FROM orders WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM idempotency_records WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM menus WHERE id = ?", menuId);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
      }
    } finally {
      if (secondContext != null) {
        secondContext.close();
      }
      if (firstContext != null) {
        firstContext.close();
      }
    }
  }

  @Test
  @DisplayName("CT-APP-001 독립 인스턴스 두 개가 같은 MySQL 상태와 멱등 결과를 공유한다")
  void sharesCommittedStateAcrossIndependentInstances() throws Exception {
    firstContext = startContext();
    secondContext = startContext();
    int firstPort = localPort(firstContext);
    int secondPort = localPort(secondContext);
    JdbcTemplate firstJdbcTemplate = firstContext.getBean(JdbcTemplate.class);
    JdbcTemplate secondJdbcTemplate = secondContext.getBean(JdbcTemplate.class);
    ObjectMapper objectMapper = firstContext.getBean(ObjectMapper.class);

    assertNotSame(firstContext, secondContext);
    assertNotSame(firstContext.getBean(DataSource.class), secondContext.getBean(DataSource.class));
    assertNotEquals(firstPort, secondPort);
    assertEquals(
        firstContext.getEnvironment().getProperty("spring.datasource.url"),
        secondContext.getEnvironment().getProperty("spring.datasource.url"));
    assertFalse(firstContext.containsBean("outboxWorker"));
    assertFalse(secondContext.containsBean("outboxWorker"));

    userId = ID_SEQUENCE.addAndGet(2);
    menuId = userId + 1;
    insertFixtures(firstJdbcTemplate);

    JsonNode charged =
        body(
            objectMapper,
            post(
                firstPort,
                "/api/v1/points/charge",
                UUID.randomUUID().toString(),
                "{\"userId\":" + userId + ",\"amount\":5000}"),
            200);
    assertEquals(5_000L, charged.get("data").get("balance").longValue());

    JsonNode ordered =
        body(
            objectMapper,
            post(
                secondPort,
                "/api/v1/orders",
                UUID.randomUUID().toString(),
                "{\"userId\":" + userId + ",\"menuId\":" + menuId + "}"),
            201);
    assertEquals(1_000L, ordered.get("data").get("remainingBalance").longValue());
    assertEquals(1L, count(firstJdbcTemplate, "orders"));
    assertEquals(1L, count(firstJdbcTemplate, "outbox_events"));

    String sharedKey = UUID.randomUUID().toString();
    List<HttpResponse<String>> concurrentResponses =
        chargeConcurrently(firstPort, secondPort, sharedKey);
    assertTrue(concurrentResponses.stream().allMatch(response -> response.statusCode() == 200));
    JsonNode firstResult = objectMapper.readTree(concurrentResponses.get(0).body());
    JsonNode secondResult = objectMapper.readTree(concurrentResponses.get(1).body());
    assertEquals(firstResult, secondResult);
    assertTrue(firstResult.get("success").booleanValue());
    assertEquals("POINT_CHARGED", firstResult.get("code").stringValue());
    assertEquals("포인트를 충전했습니다.", firstResult.get("message").stringValue());
    assertEquals(100L, firstResult.get("data").get("chargedAmount").longValue());
    assertEquals(1_100L, firstResult.get("data").get("balance").longValue());

    assertEquals(1_100L, balance(firstJdbcTemplate));
    assertEquals(1_100L, balance(secondJdbcTemplate));
    assertEquals(3L, count(firstJdbcTemplate, "idempotency_records"));
  }

  private ConfigurableApplicationContext startContext() {
    return new SpringApplicationBuilder(CoffeeOrderSystemApplication.class)
        .profiles("test")
        .run("--server.port=0", "--outbox.worker.enabled=false", "--spring.main.banner-mode=off");
  }

  private int localPort(ConfigurableApplicationContext context) {
    Integer port = context.getEnvironment().getProperty("local.server.port", Integer.class);
    if (port == null || port == 0) {
      throw new IllegalStateException("랜덤 웹 포트를 확인할 수 없습니다.");
    }
    return port;
  }

  private void insertFixtures(JdbcTemplate jdbcTemplate) {
    Timestamp now = Timestamp.from(Instant.parse("2026-07-16T00:00:00Z"));
    jdbcTemplate.update(
        "INSERT INTO users (id, point_balance, created_at, updated_at) VALUES (?, 0, ?, ?)",
        userId,
        now,
        now);
    jdbcTemplate.update(
        "INSERT INTO menus (id, name, price, created_at) VALUES (?, '교차 인스턴스 메뉴', 4000, ?)",
        menuId,
        now);
  }

  private HttpResponse<String> post(int port, String path, String key, String body)
      throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/json")
            .header("Idempotency-Key", key)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private JsonNode body(ObjectMapper objectMapper, HttpResponse<String> response, int status)
      throws Exception {
    assertEquals(status, response.statusCode());
    return objectMapper.readTree(response.body());
  }

  private List<HttpResponse<String>> chargeConcurrently(int firstPort, int secondPort, String key)
      throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      String requestBody = "{\"userId\":" + userId + ",\"amount\":100}";
      Future<HttpResponse<String>> first =
          executor.submit(
              () -> {
                assertTrue(start.await(2, TimeUnit.SECONDS));
                return post(firstPort, "/api/v1/points/charge", key, requestBody);
              });
      Future<HttpResponse<String>> second =
          executor.submit(
              () -> {
                assertTrue(start.await(2, TimeUnit.SECONDS));
                return post(secondPort, "/api/v1/points/charge", key, requestBody);
              });
      start.countDown();
      return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
    } finally {
      executor.shutdownNow();
    }
  }

  private long balance(JdbcTemplate jdbcTemplate) {
    Long value =
        jdbcTemplate.queryForObject(
            "SELECT point_balance FROM users WHERE id = ?", Long.class, userId);
    return value == null ? 0L : value;
  }

  private long count(JdbcTemplate jdbcTemplate, String table) {
    String sql =
        switch (table) {
          case "orders" -> "SELECT COUNT(*) FROM orders WHERE user_id = ?";
          case "outbox_events" ->
              "SELECT COUNT(*) FROM outbox_events WHERE order_id IN "
                  + "(SELECT id FROM orders WHERE user_id = ?)";
          case "idempotency_records" ->
              "SELECT COUNT(*) FROM idempotency_records WHERE user_id = ?";
          default -> throw new IllegalArgumentException("허용되지 않은 테이블입니다: " + table);
        };
    Long value = jdbcTemplate.queryForObject(sql, Long.class, userId);
    return value == null ? 0L : value;
  }
}
