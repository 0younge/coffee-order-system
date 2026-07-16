package com.example.coffeeordersystem.point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.coffeeordersystem.idempotency.application.IdempotencyFacade;
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
class PointAtomicityApiTest {

  private static final AtomicLong USER_SEQUENCE = new AtomicLong(8_400_000_000L);

  @Autowired private MockMvc mockMvc;

  @Autowired private JdbcTemplate jdbcTemplate;

  @MockitoSpyBean private IdempotencyFacade idempotencyFacade;

  private long userId;

  @BeforeEach
  void setUp() {
    userId = USER_SEQUENCE.incrementAndGet();
    Timestamp now = Timestamp.from(Instant.parse("2026-07-16T00:00:00Z"));
    jdbcTemplate.update(
        "INSERT INTO users (id, point_balance, created_at, updated_at) VALUES (?, 100, ?, ?)",
        userId,
        now,
        now);
  }

  @AfterEach
  void tearDown() {
    jdbcTemplate.update("DELETE FROM idempotency_records WHERE user_id = ?", userId);
    jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
  }

  @Test
  @DisplayName("IT-POINT-002 멱등 결과 저장 뒤 장애가 나면 잔액과 멱등 레코드를 함께 롤백한다")
  void rollsBackBalanceAndCompletedIdempotencyOnFailure() throws Exception {
    doAnswer(
            invocation -> {
              invocation.callRealMethod();
              throw new DataAccessResourceFailureException("테스트용 결과 저장 후 장애");
            })
        .when(idempotencyFacade)
        .complete(anyLong(), anyInt(), anyString(), anyString(), any(Instant.class));

    mockMvc
        .perform(
            post("/api/v1/points/charge")
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":" + userId + ",\"amount\":100}"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));

    assertEquals(100L, balance());
    assertEquals(0L, idempotencyCount());
  }

  private long balance() {
    Long value =
        jdbcTemplate.queryForObject(
            "SELECT point_balance FROM users WHERE id = ?", Long.class, userId);
    return value == null ? 0L : value;
  }

  private long idempotencyCount() {
    Long value =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM idempotency_records WHERE user_id = ?", Long.class, userId);
    return value == null ? 0L : value;
  }
}
