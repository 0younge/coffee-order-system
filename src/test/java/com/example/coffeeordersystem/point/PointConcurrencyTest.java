package com.example.coffeeordersystem.point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.coffeeordersystem.common.api.ApiResponseJsonCodec;
import com.example.coffeeordersystem.point.application.ChargeCommand;
import com.example.coffeeordersystem.point.application.PointChargeResult;
import com.example.coffeeordersystem.point.application.PointFacade;
import java.math.BigInteger;
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
import java.util.concurrent.TimeoutException;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class PointConcurrencyTest {

  private static final AtomicLong USER_SEQUENCE = new AtomicLong(8_100_000_000L);

  @Autowired private PointFacade pointFacade;

  @Autowired private ApiResponseJsonCodec responseJsonCodec;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private PlatformTransactionManager transactionManager;

  private long userId;
  private long otherUserId;

  @BeforeEach
  void setUp() {
    userId = USER_SEQUENCE.addAndGet(2);
    otherUserId = userId + 1;
    insertUser(userId, 0L);
    insertUser(otherUserId, 0L);
  }

  @AfterEach
  void tearDown() {
    jdbcTemplate.update(
        "DELETE FROM idempotency_records WHERE user_id IN (?, ?)", userId, otherUserId);
    jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", userId, otherUserId);
  }

  @Test
  @DisplayName("CT-POINT-001 같은 사용자의 서로 다른 충전 요청은 합계 손실 없이 직렬화된다")
  void serializesConcurrentChargesForSameUser() throws Exception {
    int requestCount = 8;
    List<ChargeCommand> commands = new ArrayList<>();
    for (int index = 0; index < requestCount; index++) {
      commands.add(command(userId, 100L, UUID.randomUUID().toString()));
    }

    List<PointChargeResult> results = executeConcurrently(commands);

    assertEquals(requestCount, results.size());
    assertTrue(results.stream().allMatch(result -> result.httpStatus() == 200));
    assertEquals(800L, balance(userId));
    assertEquals(requestCount, idempotencyCount(userId));
  }

  @Test
  @DisplayName("IT-POINT-001 사용자 행 락을 보유한 동안 같은 사용자의 충전은 대기한다")
  void waitsForSameUserLock() throws Exception {
    ExecutorService requestExecutor = Executors.newSingleThreadExecutor();
    try (HeldUserLock heldLock = holdUserLock(userId)) {
      Future<PointChargeResult> request =
          requestExecutor.submit(
              () -> pointFacade.charge(command(userId, 100L, UUID.randomUUID().toString())));

      assertThrows(TimeoutException.class, () -> request.get(300, TimeUnit.MILLISECONDS));
      assertFalse(request.isDone());

      heldLock.release();
      assertEquals(200, request.get(2, TimeUnit.SECONDS).httpStatus());
      assertEquals(100L, balance(userId));
    } finally {
      requestExecutor.shutdownNow();
    }
  }

  @Test
  @DisplayName("CT-POINT-002 한 사용자 행 락은 다른 사용자의 충전을 막지 않는다")
  void doesNotBlockDifferentUser() throws Exception {
    ExecutorService requestExecutor = Executors.newSingleThreadExecutor();
    try (HeldUserLock ignored = holdUserLock(userId)) {
      Future<PointChargeResult> request =
          requestExecutor.submit(
              () -> pointFacade.charge(command(otherUserId, 100L, UUID.randomUUID().toString())));

      assertEquals(200, request.get(2, TimeUnit.SECONDS).httpStatus());
      assertEquals(100L, balance(otherUserId));
      assertEquals(0L, balance(userId));
    } finally {
      requestExecutor.shutdownNow();
    }
  }

  @Test
  @DisplayName("CT-IDEM-001 같은 키·같은 요청이 동시에 도착해도 한 번만 충전한다")
  void appliesConcurrentSameIdempotentRequestOnce() throws Exception {
    String key = UUID.randomUUID().toString();
    List<ChargeCommand> commands = new ArrayList<>();
    for (int index = 0; index < 8; index++) {
      commands.add(command(userId, 100L, key));
    }

    List<PointChargeResult> results = executeConcurrently(commands);

    assertTrue(results.stream().allMatch(result -> result.httpStatus() == 200));
    assertTrue(
        results.stream()
            .allMatch(
                result ->
                    responseJsonCodec
                            .read(result.responseBody())
                            .get("data")
                            .get("balance")
                            .longValue()
                        == 100L));
    assertEquals(100L, balance(userId));
    assertEquals(1L, idempotencyCount(userId));
  }

  @Test
  @DisplayName("CT-IDEM-002 같은 키의 다른 금액이 동시에 도착하면 한 요청만 반영한다")
  void rejectsOneOfConcurrentDifferentRequestsWithSameKey() throws Exception {
    String key = UUID.randomUUID().toString();
    List<PointChargeResult> results =
        executeConcurrently(List.of(command(userId, 100L, key), command(userId, 200L, key)));

    Set<String> resultCodes =
        results.stream()
            .map(result -> responseJsonCodec.read(result.responseBody()).get("code").stringValue())
            .collect(Collectors.toSet());
    assertEquals(Set.of("POINT_CHARGED", "IDEMPOTENCY_KEY_REUSED"), resultCodes);
    assertTrue(Set.of(100L, 200L).contains(balance(userId)));
    assertEquals(1L, idempotencyCount(userId));
  }

  private List<PointChargeResult> executeConcurrently(List<ChargeCommand> commands)
      throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(commands.size());
    try {
      List<Future<PointChargeResult>> requests =
          commands.stream()
              .map(
                  command ->
                      executor.submit(
                          () -> {
                            assertTrue(start.await(2, TimeUnit.SECONDS));
                            return pointFacade.charge(command);
                          }))
              .toList();
      start.countDown();

      List<PointChargeResult> results = new ArrayList<>();
      for (Future<PointChargeResult> request : requests) {
        results.add(request.get(10, TimeUnit.SECONDS));
      }
      return results;
    } finally {
      executor.shutdownNow();
    }
  }

  private HeldUserLock holdUserLock(long lockedUserId) throws Exception {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    Future<?> transaction =
        executor.submit(
            () ->
                transactionTemplate.executeWithoutResult(
                    status -> {
                      jdbcTemplate.queryForObject(
                          "SELECT id FROM users WHERE id = ? FOR UPDATE", Long.class, lockedUserId);
                      locked.countDown();
                      await(release);
                    }));
    if (!locked.await(2, TimeUnit.SECONDS)) {
      release.countDown();
      executor.shutdownNow();
      throw new IllegalStateException("사용자 행 락을 획득하지 못했습니다.");
    }
    return new HeldUserLock(executor, release, transaction);
  }

  private void await(CountDownLatch latch) {
    try {
      assertTrue(latch.await(10, TimeUnit.SECONDS));
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("락 대기 스레드가 중단되었습니다.", exception);
    }
  }

  private ChargeCommand command(long targetUserId, long amount, String idempotencyKey) {
    return ChargeCommand.from(targetUserId, BigInteger.valueOf(amount), idempotencyKey);
  }

  private void insertUser(long targetUserId, long balance) {
    Timestamp now = Timestamp.from(Instant.parse("2026-07-16T00:00:00Z"));
    jdbcTemplate.update(
        "INSERT INTO users (id, point_balance, created_at, updated_at) VALUES (?, ?, ?, ?)",
        targetUserId,
        balance,
        now,
        now);
  }

  private long balance(long targetUserId) {
    Long value =
        jdbcTemplate.queryForObject(
            "SELECT point_balance FROM users WHERE id = ?", Long.class, targetUserId);
    return value == null ? 0L : value;
  }

  private long idempotencyCount(long targetUserId) {
    Long value =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM idempotency_records WHERE user_id = ?", Long.class, targetUserId);
    return value == null ? 0L : value;
  }

  private static final class HeldUserLock implements AutoCloseable {

    private final ExecutorService executor;
    private final CountDownLatch release;
    private final Future<?> transaction;
    private boolean released;

    private HeldUserLock(ExecutorService executor, CountDownLatch release, Future<?> transaction) {
      this.executor = executor;
      this.release = release;
      this.transaction = transaction;
    }

    private void release() throws Exception {
      if (!released) {
        release.countDown();
        transaction.get(2, TimeUnit.SECONDS);
        released = true;
      }
    }

    @Override
    public void close() throws Exception {
      release();
      executor.shutdownNow();
    }
  }
}
