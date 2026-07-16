package com.example.coffeeordersystem.point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PointResilienceApiTest {

  private static final AtomicLong USER_SEQUENCE = new AtomicLong(8_200_000_000L);

  @Autowired private MockMvc mockMvc;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private PlatformTransactionManager transactionManager;

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
  @DisplayName("IT-RESILIENCE-001 사용자 행 락 타임아웃은 503이며 멱등 선점도 롤백한다")
  void rollsBackIdempotencyClaimOnLockTimeout() throws Exception {
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<?> lockHolder =
          executor.submit(
              () ->
                  transactionTemplate.executeWithoutResult(
                      status -> {
                        jdbcTemplate.queryForObject(
                            "SELECT id FROM users WHERE id = ? FOR UPDATE", Long.class, userId);
                        locked.countDown();
                        await(release);
                      }));
      assertTrue(locked.await(2, TimeUnit.SECONDS));

      try {
        mockMvc
            .perform(
                post("/api/v1/points/charge")
                    .header("Idempotency-Key", UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"userId\":" + userId + ",\"amount\":100}"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("TEMPORARILY_UNAVAILABLE"));
      } finally {
        release.countDown();
        lockHolder.get(2, TimeUnit.SECONDS);
      }
    } finally {
      executor.shutdownNow();
    }

    assertEquals(100L, balance());
    assertEquals(0L, idempotencyCount());
  }

  private void await(CountDownLatch latch) {
    try {
      assertTrue(latch.await(10, TimeUnit.SECONDS));
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("락 대기 스레드가 중단되었습니다.", exception);
    }
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
