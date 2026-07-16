package com.example.coffeeordersystem.point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.coffeeordersystem.idempotency.application.IdempotencyFacade;
import com.example.coffeeordersystem.idempotency.application.RequestHasher;
import io.micrometer.core.instrument.MeterRegistry;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PointDeadlockApiTest {

  private static final AtomicLong USER_SEQUENCE = new AtomicLong(8_300_000_000L);
  private static final int AUXILIARY_USER_COUNT = 20;

  @Autowired private MockMvc mockMvc;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private RequestHasher requestHasher;

  @Autowired private PlatformTransactionManager transactionManager;

  @Autowired private MeterRegistry meterRegistry;

  @MockitoSpyBean private IdempotencyFacade idempotencyFacade;

  private long userId;
  private long auxiliaryUserStart;

  @BeforeEach
  void setUp() {
    userId = USER_SEQUENCE.addAndGet(AUXILIARY_USER_COUNT + 1L);
    auxiliaryUserStart = userId + 1;
    insertUser(userId);
    for (int index = 0; index < AUXILIARY_USER_COUNT; index++) {
      insertUser(auxiliaryUserStart + index);
    }
  }

  @AfterEach
  void tearDown() {
    jdbcTemplate.update("DELETE FROM idempotency_records WHERE user_id = ?", userId);
    jdbcTemplate.update(
        "DELETE FROM users WHERE id = ? OR id BETWEEN ? AND ?",
        userId,
        auxiliaryUserStart,
        auxiliaryUserStart + AUXILIARY_USER_COUNT - 1);
  }

  @Test
  @DisplayName("IT-RESILIENCE-001 deadlock 희생 요청은 503이며 업무·멱등 상태를 롤백한다")
  void rollsBackChargeAndIdempotencyOnDeadlock() throws Exception {
    double metricBefore = meterRegistry.get("coffee.db.deadlock").counter().count();
    String idempotencyKey = UUID.randomUUID().toString();
    insertProcessingIdempotency(idempotencyKey);

    CountDownLatch idempotencyLocked = new CountDownLatch(1);
    CountDownLatch serviceReachedClaim = new CountDownLatch(1);
    CountDownLatch holderMayLockUser = new CountDownLatch(1);
    CountDownLatch serviceMayClaim = new CountDownLatch(1);
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

    doAnswer(
            invocation -> {
              serviceReachedClaim.countDown();
              assertTrue(serviceMayClaim.await(2, TimeUnit.SECONDS));
              return invocation.callRealMethod();
            })
        .when(idempotencyFacade)
        .claim(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any());

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<?> lockHolder =
          executor.submit(
              () ->
                  transactionTemplate.executeWithoutResult(
                      status -> {
                        jdbcTemplate.update(
                            "UPDATE users SET point_balance = point_balance + 1 "
                                + "WHERE id BETWEEN ? AND ?",
                            auxiliaryUserStart,
                            auxiliaryUserStart + AUXILIARY_USER_COUNT - 1);
                        jdbcTemplate.queryForObject(
                            "SELECT id FROM idempotency_records "
                                + "WHERE user_id = ? AND operation_type = 'CHARGE' "
                                + "AND idempotency_key = ? FOR UPDATE",
                            Long.class,
                            userId,
                            idempotencyKey);
                        idempotencyLocked.countDown();
                        await(holderMayLockUser);
                        jdbcTemplate.queryForObject(
                            "SELECT id FROM users WHERE id = ? FOR UPDATE", Long.class, userId);
                        jdbcTemplate.update(
                            "DELETE FROM idempotency_records WHERE user_id = ?", userId);
                      }));
      assertTrue(idempotencyLocked.await(2, TimeUnit.SECONDS));

      Future<MvcResult> request =
          executor.submit(
              () ->
                  mockMvc
                      .perform(
                          post("/api/v1/points/charge")
                              .header("Idempotency-Key", idempotencyKey)
                              .contentType(MediaType.APPLICATION_JSON)
                              .content("{\"userId\":" + userId + ",\"amount\":100}"))
                      .andExpect(status().isServiceUnavailable())
                      .andExpect(jsonPath("$.success").value(false))
                      .andExpect(jsonPath("$.code").value("TEMPORARILY_UNAVAILABLE"))
                      .andReturn());
      assertTrue(serviceReachedClaim.await(2, TimeUnit.SECONDS));

      holderMayLockUser.countDown();
      serviceMayClaim.countDown();

      request.get(2, TimeUnit.SECONDS);
      lockHolder.get(2, TimeUnit.SECONDS);
    } finally {
      holderMayLockUser.countDown();
      serviceMayClaim.countDown();
      executor.shutdownNow();
    }

    assertEquals(0L, balance());
    assertEquals(0L, idempotencyCount());
    assertEquals(metricBefore + 1, meterRegistry.get("coffee.db.deadlock").counter().count());
  }

  private void insertUser(long targetUserId) {
    Timestamp now = Timestamp.from(Instant.parse("2026-07-16T00:00:00Z"));
    jdbcTemplate.update(
        "INSERT INTO users (id, point_balance, created_at, updated_at) VALUES (?, 0, ?, ?)",
        targetUserId,
        now,
        now);
  }

  private void insertProcessingIdempotency(String idempotencyKey) {
    Timestamp now = Timestamp.from(Instant.parse("2026-07-16T00:00:00Z"));
    jdbcTemplate.update(
        "INSERT INTO idempotency_records "
            + "(user_id, operation_type, idempotency_key, request_hash, status, "
            + "created_at, updated_at) VALUES (?, 'CHARGE', ?, ?, 'PROCESSING', ?, ?)",
        userId,
        idempotencyKey,
        requestHasher.hash(
            com.example.coffeeordersystem.idempotency.application.IdempotencyOperation.CHARGE,
            100L),
        now,
        now);
  }

  private void await(CountDownLatch latch) {
    try {
      assertTrue(latch.await(10, TimeUnit.SECONDS));
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("deadlock 테스트 스레드가 중단되었습니다.", exception);
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
