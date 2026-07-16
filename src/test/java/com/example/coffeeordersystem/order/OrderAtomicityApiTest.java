package com.example.coffeeordersystem.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.coffeeordersystem.outbox.OutboxEventWriter;
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
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderAtomicityApiTest {

  private static final AtomicLong ID_SEQUENCE = new AtomicLong(8_600_000_000L);

  @Autowired private MockMvc mockMvc;

  @Autowired private JdbcTemplate jdbcTemplate;

  @MockitoSpyBean private OutboxEventWriter outboxEventWriter;

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
        "INSERT INTO menus (id, name, price, created_at) VALUES (?, '원자성 메뉴', 4000, ?)",
        menuId,
        now);
  }

  @AfterEach
  void tearDown() {
    jdbcTemplate.update(
        "DELETE FROM outbox_events WHERE JSON_UNQUOTE(JSON_EXTRACT(payload, '$.userId')) = ?",
        Long.toString(userId));
    jdbcTemplate.update("DELETE FROM orders WHERE user_id = ?", userId);
    jdbcTemplate.update("DELETE FROM idempotency_records WHERE user_id = ?", userId);
    jdbcTemplate.update("DELETE FROM menus WHERE id = ?", menuId);
    jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
  }

  @Test
  @DisplayName("IT-ORDER-003 Outbox 저장 뒤 장애가 나면 주문의 모든 상태를 롤백한다")
  void rollsBackAllOrderStateWhenOutboxStepFails() throws Exception {
    doAnswer(
            invocation -> {
              invocation.callRealMethod();
              throw new DataAccessResourceFailureException("테스트용 Outbox 저장 후 장애");
            })
        .when(outboxEventWriter)
        .appendOrderPaid(anyLong(), anyLong(), anyLong(), anyLong(), any(Instant.class));

    mockMvc
        .perform(
            post("/api/v1/orders")
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":" + userId + ",\"menuId\":" + menuId + "}"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));

    assertEquals(5_000L, balance());
    assertEquals(0L, count("SELECT COUNT(*) FROM orders WHERE user_id = " + userId));
    assertEquals(
        0L,
        count(
            "SELECT COUNT(*) FROM outbox_events WHERE "
                + "JSON_UNQUOTE(JSON_EXTRACT(payload, '$.userId')) = '"
                + userId
                + "'"));
    assertEquals(0L, count("SELECT COUNT(*) FROM idempotency_records WHERE user_id = " + userId));
  }

  private long balance() {
    return count("SELECT point_balance FROM users WHERE id = " + userId);
  }

  private long count(String sql) {
    Long value = jdbcTemplate.queryForObject(sql, Long.class);
    return value == null ? 0L : value;
  }
}
