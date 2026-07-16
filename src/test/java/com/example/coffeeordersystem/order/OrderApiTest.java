package com.example.coffeeordersystem.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderApiTest {

  private static final AtomicLong ID_SEQUENCE = new AtomicLong(8_500_000_000L);

  @Autowired private MockMvc mockMvc;

  @Autowired private JdbcTemplate jdbcTemplate;

  private long userId;
  private long menuId;

  @BeforeEach
  void setUp() {
    userId = ID_SEQUENCE.addAndGet(2);
    menuId = userId + 1;
    insertUser(5_000L);
    insertMenu(menuId, "테스트 아메리카노", 4_000L);
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
  @DisplayName("IT-ORDER-001 AT-ORDER-001 주문 성공 상태를 한 트랜잭션에 저장한다")
  void placesPaidOrderWithSnapshotAndOutbox() throws Exception {
    mockMvc
        .perform(order(UUID.randomUUID().toString(), body(menuId)))
        .andExpect(status().isCreated())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("ORDER_PAID"))
        .andExpect(jsonPath("$.message").value("주문과 결제가 완료되었습니다."))
        .andExpect(jsonPath("$.data.orderId").isNumber())
        .andExpect(jsonPath("$.data.menuId").value(menuId))
        .andExpect(jsonPath("$.data.menuName").value("테스트 아메리카노"))
        .andExpect(jsonPath("$.data.paidAmount").value(4_000L))
        .andExpect(jsonPath("$.data.remainingBalance").value(1_000L))
        .andExpect(jsonPath("$.data.paidAt").isString());

    assertEquals(1_000L, balance());
    assertEquals(1L, orderCount());
    assertEquals(1L, outboxCount());
    assertEquals(1L, idempotencyCount());
    assertEquals(
        "테스트 아메리카노|4000|PAID",
        jdbcTemplate.queryForObject(
            "SELECT CONCAT(menu_name_snapshot, '|', paid_amount, '|', status) "
                + "FROM orders WHERE user_id = ?",
            String.class,
            userId));
    assertEquals(
        "ORDER_PAID|PENDING|0",
        jdbcTemplate.queryForObject(
            "SELECT CONCAT(event_type, '|', status, '|', retry_count) "
                + "FROM outbox_events WHERE order_id IN "
                + "(SELECT id FROM orders WHERE user_id = ?)",
            String.class,
            userId));
    assertEquals(
        "ORDER_PAID|" + userId + "|" + menuId + "|4000",
        jdbcTemplate.queryForObject(
            "SELECT CONCAT("
                + "JSON_UNQUOTE(JSON_EXTRACT(payload, '$.eventType')), '|', "
                + "JSON_UNQUOTE(JSON_EXTRACT(payload, '$.userId')), '|', "
                + "JSON_UNQUOTE(JSON_EXTRACT(payload, '$.menuId')), '|', "
                + "JSON_UNQUOTE(JSON_EXTRACT(payload, '$.paymentAmount'))) "
                + "FROM outbox_events WHERE order_id IN "
                + "(SELECT id FROM orders WHERE user_id = ?)",
            String.class,
            userId));
    assertEquals(
        1L,
        count(
            "SELECT COUNT(*) FROM outbox_events WHERE event_id = "
                + "JSON_UNQUOTE(JSON_EXTRACT(payload, '$.eventId')) AND "
                + "JSON_EXTRACT(payload, '$.occurredAt') IS NOT NULL AND "
                + "TIMESTAMPDIFF(MICROSECOND, next_retry_at, created_at) = 0 AND order_id IN "
                + "(SELECT id FROM orders WHERE user_id = "
                + userId
                + ")"));
  }

  @Test
  @DisplayName("AT-USER-001 존재하지 않는 사용자는 어떤 상태도 남기지 않는다")
  void rejectsMissingUserWithoutIdempotency() throws Exception {
    long missingUserId = userId + 100_000L;

    mockMvc
        .perform(
            order(
                UUID.randomUUID().toString(),
                "{\"userId\":" + missingUserId + ",\"menuId\":" + menuId + "}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));

    assertEquals(0L, count("SELECT COUNT(*) FROM orders WHERE user_id = " + missingUserId));
    assertEquals(
        0L, count("SELECT COUNT(*) FROM idempotency_records WHERE user_id = " + missingUserId));
    assertEquals(5_000L, balance());
  }

  @Test
  @DisplayName("AT-ORDER-002 잘못된 요청과 메뉴 부재를 상태 계약대로 거절한다")
  void rejectsInvalidRequestAndStoresMissingMenuResult() throws Exception {
    String validBody = body(menuId);
    mockMvc
        .perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(validBody))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    expectError("not-a-uuid", validBody, 400, "INVALID_REQUEST");
    expectError(
        UUID.randomUUID().toString(),
        "{\"userId\":" + userId + ",\"menuId\":",
        400,
        "INVALID_REQUEST");
    expectError(
        UUID.randomUUID().toString(),
        "{\"userId\":" + userId + ",\"menuId\":0}",
        400,
        "INVALID_REQUEST");
    expectError(
        UUID.randomUUID().toString(),
        "{\"userId\":" + userId + ",\"menuId\":1.5}",
        400,
        "INVALID_REQUEST");
    expectError(
        UUID.randomUUID().toString(),
        "{\"userId\":" + userId + ",\"menuId\":\"1\"}",
        400,
        "INVALID_REQUEST");
    expectError(
        UUID.randomUUID().toString(),
        "{\"userId\":" + userId + ",\"menuId\":null}",
        400,
        "INVALID_REQUEST");
    expectError(
        UUID.randomUUID().toString(),
        "{\"userId\":" + userId + ",\"menuId\":" + menuId + ",\"unknown\":true}",
        400,
        "INVALID_REQUEST");
    mockMvc
        .perform(
            post("/api/v1/orders")
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.TEXT_PLAIN)
                .content(validBody))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));

    assertEquals(5_000L, balance());
    assertEquals(0L, orderCount());
    assertEquals(0L, outboxCount());
    assertEquals(0L, idempotencyCount());

    String missingMenuKey = UUID.randomUUID().toString();
    long missingMenuId = menuId + 100_000L;
    for (int attempt = 0; attempt < 2; attempt++) {
      expectError(missingMenuKey, body(missingMenuId), 404, "MENU_NOT_FOUND");
    }
    assertEquals(1L, idempotencyCount());
    assertEquals(0L, orderCount());
    assertEquals(0L, outboxCount());
  }

  @Test
  @DisplayName("IT-ORDER-002 AT-ORDER-003 잔액 부족 결과만 멱등 저장한다")
  void storesInsufficientPointResultWithoutBusinessState() throws Exception {
    jdbcTemplate.update("UPDATE users SET point_balance = 3999 WHERE id = ?", userId);
    String key = UUID.randomUUID().toString();

    for (int attempt = 0; attempt < 2; attempt++) {
      expectError(key, body(menuId), 409, "INSUFFICIENT_POINT");
    }

    assertEquals(3_999L, balance());
    assertEquals(0L, orderCount());
    assertEquals(0L, outboxCount());
    assertEquals(1L, idempotencyCount());
  }

  @Test
  @DisplayName("IT-IDEM-001 AT-ORDER-004 같은 주문 재요청은 최초 결과를 재사용한다")
  void reusesSameOrderRequest() throws Exception {
    String key = UUID.randomUUID().toString();

    for (int attempt = 0; attempt < 2; attempt++) {
      mockMvc
          .perform(order(key, body(menuId)))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.code").value("ORDER_PAID"))
          .andExpect(jsonPath("$.data.remainingBalance").value(1_000L));
    }

    assertEquals(1_000L, balance());
    assertEquals(1L, orderCount());
    assertEquals(1L, outboxCount());
    assertEquals(1L, idempotencyCount());
  }

  @Test
  @DisplayName("IT-IDEM-002 AT-ORDER-005 같은 키의 다른 메뉴는 추가 결제 없이 거절한다")
  void rejectsDifferentMenuWithSameKey() throws Exception {
    long otherMenuId = menuId + 10_000L;
    insertMenu(otherMenuId, "테스트 라떼", 1_000L);
    String key = UUID.randomUUID().toString();

    mockMvc.perform(order(key, body(menuId))).andExpect(status().isCreated());
    expectError(key, body(otherMenuId), 409, "IDEMPOTENCY_KEY_REUSED");

    assertEquals(1_000L, balance());
    assertEquals(1L, orderCount());
    assertEquals(1L, outboxCount());
    assertEquals(1L, idempotencyCount());
  }

  @Test
  @DisplayName("AT-ORDER-001 주문 뒤 메뉴가 바뀌어도 주문 스냅샷은 유지된다")
  void preservesOrderSnapshotWhenMenuChanges() throws Exception {
    mockMvc
        .perform(order(UUID.randomUUID().toString(), body(menuId)))
        .andExpect(status().isCreated());

    jdbcTemplate.update("UPDATE menus SET name = '변경된 메뉴', price = 9000 WHERE id = ?", menuId);

    assertEquals(
        "테스트 아메리카노|4000",
        jdbcTemplate.queryForObject(
            "SELECT CONCAT(menu_name_snapshot, '|', paid_amount) FROM orders WHERE user_id = ?",
            String.class,
            userId));
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder order(
      String idempotencyKey, String requestBody) {
    return post("/api/v1/orders")
        .header("Idempotency-Key", idempotencyKey)
        .contentType(MediaType.APPLICATION_JSON)
        .content(requestBody);
  }

  private void expectError(
      String idempotencyKey, String requestBody, int httpStatus, String errorCode)
      throws Exception {
    mockMvc
        .perform(order(idempotencyKey, requestBody))
        .andExpect(status().is(httpStatus))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value(errorCode))
        .andExpect(jsonPath("$.data").doesNotExist());
  }

  private String body(long targetMenuId) {
    return "{\"userId\":" + userId + ",\"menuId\":" + targetMenuId + "}";
  }

  private void insertUser(long balance) {
    Timestamp now = Timestamp.from(Instant.parse("2026-07-16T00:00:00Z"));
    jdbcTemplate.update(
        "INSERT INTO users (id, point_balance, created_at, updated_at) VALUES (?, ?, ?, ?)",
        userId,
        balance,
        now,
        now);
  }

  private void insertMenu(long targetMenuId, String name, long price) {
    jdbcTemplate.update(
        "INSERT INTO menus (id, name, price, created_at) VALUES (?, ?, ?, ?)",
        targetMenuId,
        name,
        price,
        Timestamp.from(Instant.parse("2026-07-16T00:00:00Z")));
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
