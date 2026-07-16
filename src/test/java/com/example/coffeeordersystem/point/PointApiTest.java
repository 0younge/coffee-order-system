package com.example.coffeeordersystem.point;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.coffeeordersystem.common.api.ApiResponseJsonCodec;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;
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
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PointApiTest {

  private static final AtomicLong USER_SEQUENCE = new AtomicLong(8_000_000_000L);

  @Autowired private MockMvc mockMvc;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private ApiResponseJsonCodec responseJsonCodec;

  private long userId;

  @BeforeEach
  void setUp() {
    userId = USER_SEQUENCE.incrementAndGet();
    insertUser(100L);
  }

  @AfterEach
  void tearDown() {
    jdbcTemplate.update("DELETE FROM idempotency_records WHERE user_id = ?", userId);
    jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
  }

  @Test
  @DisplayName("AT-POINT-001 기존 사용자의 포인트를 충전하고 최종 잔액을 반환한다")
  void chargesExistingUser() throws Exception {
    String key = UUID.randomUUID().toString();

    mockMvc
        .perform(charge(key, "{\"userId\":" + userId + ",\"amount\":250}"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("POINT_CHARGED"))
        .andExpect(jsonPath("$.message").value("포인트를 충전했습니다."))
        .andExpect(jsonPath("$.data.chargedAmount").value(250))
        .andExpect(jsonPath("$.data.balance").value(350));

    assertEquals(350L, balance());
    assertEquals(1L, idempotencyCount());
    assertEquals(
        "COMPLETED",
        jdbcTemplate.queryForObject(
            "SELECT status FROM idempotency_records WHERE user_id = ?", String.class, userId));
  }

  @Test
  @DisplayName("AT-USER-001 AT-CONTRACT-003 존재하지 않는 사용자를 먼저 거절한다")
  void rejectsMissingUserBeforeIdempotencyClaim() throws Exception {
    long missingUserId = userId + 100_000L;

    mockMvc
        .perform(
            charge(
                UUID.randomUUID().toString(), "{\"userId\":" + missingUserId + ",\"amount\":100}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
        .andExpect(jsonPath("$.data").hasJsonPath())
        .andExpect(jsonPath("$.data").value(nullValue()));

    assertEquals(
        0L, count("SELECT COUNT(*) FROM idempotency_records WHERE user_id = " + missingUserId));
    assertEquals(100L, balance());
  }

  @Test
  @DisplayName("AT-POINT-002 AT-CONTRACT-001 형식 오류를 상태 변경 없이 먼저 거절한다")
  void rejectsInvalidRequestsBeforeStateChange() throws Exception {
    String validBody = "{\"userId\":" + userId + ",\"amount\":100}";

    mockMvc
        .perform(
            post("/api/v1/points/charge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    expectError("not-a-uuid", validBody, 400, "INVALID_REQUEST");
    expectError(
        UUID.randomUUID().toString(),
        "{\"userId\":" + userId + ",\"amount\":",
        400,
        "INVALID_REQUEST");
    expectError(UUID.randomUUID().toString(), body("0"), 400, "INVALID_CHARGE_AMOUNT");
    expectError(UUID.randomUUID().toString(), body("-1"), 400, "INVALID_CHARGE_AMOUNT");
    expectError(UUID.randomUUID().toString(), body("1.0"), 400, "INVALID_CHARGE_AMOUNT");
    expectError(UUID.randomUUID().toString(), body("1.5"), 400, "INVALID_CHARGE_AMOUNT");
    expectError(UUID.randomUUID().toString(), body("1e2"), 400, "INVALID_CHARGE_AMOUNT");
    expectError(
        UUID.randomUUID().toString(), body("9223372036854775808"), 400, "INVALID_CHARGE_AMOUNT");
    expectError(UUID.randomUUID().toString(), body("\"100\""), 400, "INVALID_REQUEST");
    expectError(
        UUID.randomUUID().toString(),
        "{\"userId\":" + userId + ",\"amount\":100,\"unknown\":true}",
        400,
        "INVALID_REQUEST");
    expectError(
        UUID.randomUUID().toString(),
        "{\"userId\":" + userId + ",\"amount\":null}",
        400,
        "INVALID_REQUEST");
    expectError(
        UUID.randomUUID().toString(), "{\"userId\":0,\"amount\":100}", 400, "INVALID_REQUEST");
    expectError(
        UUID.randomUUID().toString(),
        "{\"userId\":" + userId + ".0,\"amount\":100}",
        400,
        "INVALID_REQUEST");
    expectError(
        UUID.randomUUID().toString(),
        "{\"userId\":" + userId + ".5,\"amount\":100}",
        400,
        "INVALID_REQUEST");
    expectError(
        UUID.randomUUID().toString(),
        "{\"userId\":" + userId + "e0,\"amount\":100}",
        400,
        "INVALID_REQUEST");
    mockMvc
        .perform(
            post("/api/v1/points/charge")
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.TEXT_PLAIN)
                .content(validBody))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));

    assertEquals(100L, balance());
    assertEquals(0L, idempotencyCount());
  }

  @Test
  @DisplayName("AT-POINT-002 잔액 덧셈 overflow 결과를 저장하고 재사용한다")
  void storesAndReusesOverflowResult() throws Exception {
    jdbcTemplate.update("UPDATE users SET point_balance = ? WHERE id = ?", Long.MAX_VALUE, userId);
    String key = UUID.randomUUID().toString();
    String request = body("1");

    for (int attempt = 0; attempt < 2; attempt++) {
      mockMvc
          .perform(charge(key, request))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.success").value(false))
          .andExpect(jsonPath("$.code").value("POINT_BALANCE_OVERFLOW"))
          .andExpect(jsonPath("$.data").hasJsonPath())
          .andExpect(jsonPath("$.data").value(nullValue()));
    }

    assertEquals(Long.MAX_VALUE, balance());
    assertEquals(1L, idempotencyCount());
    assertEquals(
        "POINT_BALANCE_OVERFLOW",
        jdbcTemplate.queryForObject(
            "SELECT result_code FROM idempotency_records WHERE user_id = ?", String.class, userId));
  }

  @Test
  @DisplayName("IT-IDEM-001 AT-POINT-003 같은 키·같은 요청은 최초 응답을 재사용하고 한 번만 충전한다")
  void reusesSameIdempotentRequest() throws Exception {
    String key = UUID.randomUUID().toString();
    String request = body("250");

    MvcResult first =
        mockMvc
            .perform(charge(key, request))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("POINT_CHARGED"))
            .andExpect(jsonPath("$.data.balance").value(350))
            .andReturn();
    MvcResult replay =
        mockMvc
            .perform(charge(key, request))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("POINT_CHARGED"))
            .andExpect(jsonPath("$.data.balance").value(350))
            .andReturn();

    String firstBody = first.getResponse().getContentAsString();
    assertEquals(first.getResponse().getStatus(), replay.getResponse().getStatus());
    assertEquals(firstBody, replay.getResponse().getContentAsString());
    assertEquals(
        firstBody,
        responseJsonCodec.compact(
            jdbcTemplate.queryForObject(
                "SELECT response_body FROM idempotency_records WHERE user_id = ?",
                String.class,
                userId)));

    assertEquals(350L, balance());
    assertEquals(1L, idempotencyCount());
  }

  @Test
  @DisplayName("IT-IDEM-002 AT-POINT-004 AT-CONTRACT-003 멱등 충돌을 먼저 판정한다")
  void rejectsDifferentRequestWithSameKey() throws Exception {
    String key = UUID.randomUUID().toString();

    mockMvc.perform(charge(key, body("100"))).andExpect(status().isOk());
    mockMvc
        .perform(charge(key, body("200")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

    assertEquals(200L, balance());
    assertEquals(1L, idempotencyCount());
  }

  @Test
  @DisplayName("AT-CONTRACT-001 대문자 UUID는 표준 소문자로 저장한다")
  void storesNormalizedUuid() throws Exception {
    String uppercaseKey = UUID.randomUUID().toString().toUpperCase(Locale.ROOT);

    mockMvc.perform(charge(uppercaseKey, body("100"))).andExpect(status().isOk());

    assertEquals(
        uppercaseKey.toLowerCase(Locale.ROOT),
        jdbcTemplate.queryForObject(
            "SELECT idempotency_key FROM idempotency_records WHERE user_id = ?",
            String.class,
            userId));
  }

  @Test
  @DisplayName("AT-CONTRACT-004 JSON을 거절한 충전 요청은 상태를 변경하기 전에 406을 반환한다")
  void rejectsUnacceptableResponseBeforeCharge() throws Exception {
    mockMvc
        .perform(charge(UUID.randomUUID().toString(), body("100")).accept(MediaType.TEXT_PLAIN))
        .andExpect(status().isNotAcceptable())
        .andExpect(content().string(""));

    assertEquals(100L, balance());
    assertEquals(0L, idempotencyCount());
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder charge(
      String idempotencyKey, String body) {
    return post("/api/v1/points/charge")
        .header("Idempotency-Key", idempotencyKey)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  private void expectError(
      String idempotencyKey, String requestBody, int httpStatus, String errorCode)
      throws Exception {
    mockMvc
        .perform(charge(idempotencyKey, requestBody))
        .andExpect(status().is(httpStatus))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value(errorCode))
        .andExpect(jsonPath("$.data").hasJsonPath())
        .andExpect(jsonPath("$.data").value(nullValue()));
  }

  private String body(String amount) {
    return "{\"userId\":" + userId + ",\"amount\":" + amount + "}";
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

  private long balance() {
    Long value =
        jdbcTemplate.queryForObject(
            "SELECT point_balance FROM users WHERE id = ?", Long.class, userId);
    return value == null ? 0 : value;
  }

  private long idempotencyCount() {
    return count("SELECT COUNT(*) FROM idempotency_records WHERE user_id = " + userId);
  }

  private long count(String sql) {
    Long value = jdbcTemplate.queryForObject(sql, Long.class);
    return value == null ? 0 : value;
  }
}
