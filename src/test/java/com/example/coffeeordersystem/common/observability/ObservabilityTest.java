package com.example.coffeeordersystem.common.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.coffeeordersystem.common.error.GlobalExceptionHandler;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class ObservabilityTest {

  private static final AtomicLong ID_SEQUENCE = new AtomicLong(9_200_000_000L);
  private static final Pattern REQUEST_ID_PATTERN =
      Pattern.compile("requestId=\\\"?([0-9a-f-]{36})\\\"?");

  @Autowired private MockMvc mockMvc;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private MeterRegistry meterRegistry;

  @Autowired private Environment environment;

  @Autowired private GlobalExceptionHandler exceptionHandler;

  private long userId;
  private long menuId;

  @BeforeEach
  void setUp() {
    userId = ID_SEQUENCE.addAndGet(1_000);
    menuId = userId + 1;
    Timestamp now = Timestamp.from(Instant.now());
    jdbcTemplate.update(
        "INSERT INTO users (id, point_balance, created_at, updated_at) VALUES (?, 10000, ?, ?)",
        userId,
        now,
        now);
    jdbcTemplate.update(
        "INSERT INTO menus (id, name, price, created_at) VALUES (?, '관측 메뉴', 4000, ?)",
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
    jdbcTemplate.update("DELETE FROM menus WHERE id = ?", menuId);
    jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
  }

  @Test
  @DisplayName("QT-OBS-001 요청·사용자·주문·이벤트를 key-value로 연결하고 비밀값을 숨긴다")
  void correlatesBusinessLogsWithoutSecrets(CapturedOutput output) throws Exception {
    String idempotencyKey = UUID.randomUUID().toString();

    mockMvc
        .perform(
            post("/api/v1/orders")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":" + userId + ",\"menuId\":" + menuId + "}"))
        .andExpect(status().isCreated());

    long orderId =
        jdbcTemplate.queryForObject("SELECT id FROM orders WHERE user_id = ?", Long.class, userId);
    String eventId =
        jdbcTemplate.queryForObject(
            "SELECT event_id FROM outbox_events WHERE order_id = ?", String.class, orderId);
    exceptionHandler.handleUnexpectedException(
        new IllegalStateException("SQL error DB_PASSWORD=top-secret"));
    String logs = output.getAll();
    String orderLog = lineContaining(logs, "주문 처리 완료");
    String requestLog = lineContaining(logs, "HTTP 요청 완료");
    Matcher requestIdMatcher = REQUEST_ID_PATTERN.matcher(orderLog);

    assertTrue(requestIdMatcher.find());
    assertTrue(containsKeyValue(orderLog, "userId", Long.toString(userId)));
    assertTrue(containsKeyValue(orderLog, "orderId", Long.toString(orderId)));
    assertTrue(containsKeyValue(orderLog, "eventId", eventId));
    assertTrue(containsKeyValue(requestLog, "requestId", requestIdMatcher.group(1)));
    assertFalse(logs.contains(idempotencyKey));
    assertFalse(logs.contains("top-secret"));
    assertFalse(logs.contains("DB_PASSWORD"));
  }

  @Test
  @DisplayName("QT-CONFIG-001 QT-OBS-002 health 경계와 기본·사용자 지표를 등록한다")
  void exposesOnlyHealthAndRegistersApprovedMetrics() throws Exception {
    double pendingBefore = meterRegistry.get("coffee.outbox.pending").gauge().value();
    double failedBefore = meterRegistry.get("coffee.outbox.failed").gauge().value();
    Instant now = Instant.now();
    insertPendingEvent(insertOrder(now.minusSeconds(120)), now.minusSeconds(120));
    insertFailedEvent(insertOrder(now), now);

    mockMvc.perform(get("/api/v1/menus")).andExpect(status().isOk());
    mockMvc
        .perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.components").doesNotExist())
        .andExpect(jsonPath("$.details").doesNotExist());
    mockMvc.perform(get("/actuator/metrics")).andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/v1/not-defined"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));

    assertEquals("health", environment.getProperty("management.endpoints.web.exposure.include"));
    assertEquals("never", environment.getProperty("management.endpoint.health.show-details"));
    assertTrue(meterRegistry.find("http.server.requests").meters().iterator().hasNext());
    assertTrue(
        meterRegistry.getMeters().stream()
            .map(meter -> meter.getId().getName())
            .anyMatch(name -> name.startsWith("hikaricp.connections")));
    assertEquals(pendingBefore + 1, meterRegistry.get("coffee.outbox.pending").gauge().value());
    assertEquals(failedBefore + 1, meterRegistry.get("coffee.outbox.failed").gauge().value());
    assertTrue(
        meterRegistry.get("coffee.outbox.oldest.pending.age").gauge().value() >= 100,
        "oldest pending age가 준비한 120초 이벤트를 반영해야 합니다.");

    Set<String> customMeterNames =
        meterRegistry.getMeters().stream()
            .map(Meter::getId)
            .filter(id -> id.getName().startsWith("coffee."))
            .peek(id -> assertTrue(id.getTags().isEmpty(), id.getName()))
            .map(Meter.Id::getName)
            .collect(java.util.stream.Collectors.toSet());
    assertEquals(
        Set.of(
            "coffee.db.lock.timeout",
            "coffee.db.deadlock",
            "coffee.outbox.delivery.published",
            "coffee.outbox.delivery.retried",
            "coffee.outbox.delivery.failed",
            "coffee.outbox.fencing.rejected",
            "coffee.outbox.pending",
            "coffee.outbox.failed",
            "coffee.outbox.oldest.pending.age"),
        customMeterNames);
  }

  private long insertOrder(Instant createdAt) {
    long orderId = ID_SEQUENCE.incrementAndGet();
    Timestamp timestamp = Timestamp.from(createdAt);
    jdbcTemplate.update(
        "INSERT INTO orders "
            + "(id, user_id, menu_id, menu_name_snapshot, paid_amount, status, paid_at, created_at) "
            + "VALUES (?, ?, ?, '관측 메뉴', 4000, 'PAID', ?, ?)",
        orderId,
        userId,
        menuId,
        timestamp,
        timestamp);
    return orderId;
  }

  private void insertPendingEvent(long orderId, Instant createdAt) {
    String eventId = UUID.randomUUID().toString();
    Timestamp timestamp = Timestamp.from(createdAt);
    jdbcTemplate.update(
        "INSERT INTO outbox_events "
            + "(event_id, order_id, event_type, payload, status, retry_count, next_retry_at, "
            + "created_at) VALUES (?, ?, 'ORDER_PAID', JSON_OBJECT('eventId', ?), "
            + "'PENDING', 0, ?, ?)",
        eventId,
        orderId,
        eventId,
        timestamp,
        timestamp);
  }

  private void insertFailedEvent(long orderId, Instant failedAt) {
    String eventId = UUID.randomUUID().toString();
    Timestamp timestamp = Timestamp.from(failedAt);
    jdbcTemplate.update(
        "INSERT INTO outbox_events "
            + "(event_id, order_id, event_type, payload, status, retry_count, failed_at, "
            + "last_error_type, created_at) VALUES (?, ?, 'ORDER_PAID', "
            + "JSON_OBJECT('eventId', ?), 'FAILED', 1, ?, 'NETWORK', ?)",
        eventId,
        orderId,
        eventId,
        timestamp,
        timestamp);
  }

  private boolean containsKeyValue(String logs, String key, String value) {
    return logs.contains(key + "=\"" + value + "\"") || logs.contains(key + "=" + value);
  }

  private String lineContaining(String logs, String text) {
    return logs.lines().filter(line -> line.contains(text)).findFirst().orElseThrow();
  }
}
