package com.example.coffeeordersystem.outbox.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.coffeeordersystem.outbox.domain.OutboxClaim;
import com.example.coffeeordersystem.outbox.domain.OutboxDeliveryResult;
import com.example.coffeeordersystem.outbox.domain.OutboxErrorType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class OutboxStoreTest {

  private static final AtomicLong ID_SEQUENCE = new AtomicLong(8_900_000_000L);
  private static final Instant NOW = Instant.parse("2026-07-16T12:00:00Z");

  @Autowired private OutboxStore outboxStore;

  @Autowired private JdbcTemplate jdbcTemplate;

  private long userId;
  private long menuId;

  @BeforeEach
  void setUp() {
    userId = ID_SEQUENCE.addAndGet(1_000);
    menuId = userId + 1;
    Timestamp now = Timestamp.from(NOW);
    jdbcTemplate.update(
        "INSERT INTO users (id, point_balance, created_at, updated_at) VALUES (?, 10000, ?, ?)",
        userId,
        now,
        now);
    jdbcTemplate.update(
        "INSERT INTO menus (id, name, price, created_at) VALUES (?, 'Outbox 메뉴', 4000, ?)",
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
    jdbcTemplate.update("DELETE FROM menus WHERE id = ?", menuId);
    jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
  }

  @Test
  @DisplayName("IT-OUTBOX-001 전송 시각이 된 행과 만료된 lease를 순서대로 선점한다")
  void claimsDueAndExpiredRowsInOrder() {
    String expired =
        insertProcessingEvent(NOW.minusSeconds(100), NOW.minusSeconds(31), NOW.minusSeconds(200));
    String expiredBoundary =
        insertProcessingEvent(NOW.minusSeconds(90), NOW.minusSeconds(30), NOW.minusSeconds(190));
    String firstDue = insertPendingEvent(0, NOW.minusSeconds(2), NOW.minusSeconds(4), null, null);
    String secondDue = insertPendingEvent(0, NOW.minusSeconds(2), NOW.minusSeconds(3), null, null);
    String dueBoundary = insertPendingEvent(0, NOW, NOW.minusSeconds(2), null, null);
    String future = insertPendingEvent(0, NOW.plusSeconds(1), NOW.minusSeconds(2), null, null);
    String active =
        insertProcessingEvent(NOW.minusSeconds(100), NOW.minusSeconds(29), NOW.minusSeconds(1));

    List<OutboxClaim> firstBatch = outboxStore.claim(NOW, 3);
    List<OutboxClaim> secondBatch = outboxStore.claim(NOW, 10);

    assertEquals(List.of(expired, expiredBoundary, firstDue), eventIds(firstBatch));
    assertEquals(List.of(secondDue, dueBoundary), eventIds(secondBatch));
    assertTrue(firstBatch.stream().allMatch(claim -> claim.claimToken().length() == 36));
    assertEquals("PENDING", status(future));
    assertEquals("PROCESSING", status(active));
    assertEquals(7L, count("SELECT COUNT(*) FROM outbox_events WHERE order_id IN " + orderIds()));
  }

  @Test
  @DisplayName("EXT-OUTBOX-004 EXT-OUTBOX-005 EXT-OUTBOX-009 종결 상태를 정규화한다")
  void normalizesEveryDeliveryState() {
    String retryThenPublish = insertPendingEvent(0, NOW, NOW.minusSeconds(3), null, null);
    OutboxClaim firstClaim = outboxStore.claim(NOW, 1).get(0);
    Instant firstFailureAt = NOW.plusSeconds(1);

    assertTrue(
        outboxStore.fail(
            retryThenPublish,
            firstClaim.claimToken(),
            OutboxDeliveryResult.failed(true, OutboxErrorType.HTTP_5XX, 503),
            firstFailureAt));
    assertEquals("PENDING|1|2026-07-16 12:01:01.000000|503|HTTP_5XX", lifecycle(retryThenPublish));

    OutboxClaim retryClaim = outboxStore.claim(firstFailureAt.plusSeconds(60), 1).get(0);
    assertTrue(outboxStore.publish(retryThenPublish, retryClaim.claimToken(), NOW.plusSeconds(62)));
    assertEquals("PUBLISHED|1|null|null|null", lifecycle(retryThenPublish));
    assertEquals(
        1L,
        count(
            "SELECT COUNT(*) FROM outbox_events WHERE event_id = '"
                + retryThenPublish
                + "' AND next_retry_at IS NULL AND locked_at IS NULL AND claim_token IS NULL "
                + "AND published_at IS NOT NULL AND failed_at IS NULL"));

    String permanent = insertPendingEvent(0, NOW, NOW.minusSeconds(2), null, null);
    OutboxClaim permanentClaim = outboxStore.claim(NOW, 1).get(0);
    assertTrue(
        outboxStore.fail(
            permanent,
            permanentClaim.claimToken(),
            OutboxDeliveryResult.failed(false, OutboxErrorType.HTTP_4XX, 422),
            NOW.plusSeconds(2)));
    assertEquals("FAILED|1|null|422|HTTP_4XX", lifecycle(permanent));

    String exhausted =
        insertPendingEvent(3, NOW, NOW.minusSeconds(1), 500, OutboxErrorType.HTTP_5XX);
    OutboxClaim exhaustedClaim = outboxStore.claim(NOW, 1).get(0);
    assertTrue(
        outboxStore.fail(
            exhausted,
            exhaustedClaim.claimToken(),
            OutboxDeliveryResult.failed(true, OutboxErrorType.HTTP_5XX, 503),
            NOW.plusSeconds(3)));
    assertEquals("FAILED|4|null|503|HTTP_5XX", lifecycle(exhausted));
  }

  @Test
  @DisplayName("EXT-OUTBOX-003 모든 일시 실패의 횟수·예약·오류를 기록한다")
  void recordsEveryRetryableFailure() {
    List<OutboxDeliveryResult> failures =
        List.of(
            OutboxDeliveryResult.failed(true, OutboxErrorType.TIMEOUT, null),
            OutboxDeliveryResult.failed(true, OutboxErrorType.NETWORK, null),
            OutboxDeliveryResult.failed(true, OutboxErrorType.HTTP_4XX, 408),
            OutboxDeliveryResult.failed(true, OutboxErrorType.HTTP_4XX, 429),
            OutboxDeliveryResult.failed(true, OutboxErrorType.HTTP_5XX, 500));

    for (int index = 0; index < failures.size(); index++) {
      String eventId = insertPendingEvent(0, NOW, NOW.plusNanos(index * 1_000L), null, null);
      OutboxClaim claim = outboxStore.claim(NOW.plusSeconds(1), 1).get(0);
      OutboxDeliveryResult failure = failures.get(index);

      assertTrue(outboxStore.fail(eventId, claim.claimToken(), failure, NOW.plusSeconds(2)));
      assertEquals("PENDING", status(eventId));
      assertEquals(1, retryCount(eventId));
      assertEquals(
          failure.errorType().name(),
          jdbcTemplate.queryForObject(
              "SELECT last_error_type FROM outbox_events WHERE event_id = ?",
              String.class,
              eventId));
      assertEquals(
          failure.httpStatus(),
          jdbcTemplate.queryForObject(
              "SELECT last_http_status FROM outbox_events WHERE event_id = ?",
              Integer.class,
              eventId));
      assertEquals(
          Timestamp.from(NOW.plusSeconds(62)),
          jdbcTemplate.queryForObject(
              "SELECT next_retry_at FROM outbox_events WHERE event_id = ?",
              Timestamp.class,
              eventId));
    }
  }

  @Test
  @DisplayName("IT-OUTBOX-002 CT-OUTBOX-002 EXT-OUTBOX-007 만료 뒤 이전 claim 결과를 차단한다")
  void fencesLateResultAfterLeaseRecovery() {
    String eventId = insertPendingEvent(0, NOW, NOW.minusSeconds(1), null, null);
    OutboxClaim oldClaim = outboxStore.claim(NOW, 1).get(0);
    jdbcTemplate.update(
        "UPDATE outbox_events SET locked_at = ? WHERE event_id = ?",
        Timestamp.from(NOW.minusSeconds(31)),
        eventId);

    OutboxClaim currentClaim = outboxStore.claim(NOW, 1).get(0);

    assertNotEquals(oldClaim.claimToken(), currentClaim.claimToken());
    assertFalse(outboxStore.publish(eventId, oldClaim.claimToken(), NOW.plusSeconds(1)));
    assertFalse(
        outboxStore.fail(
            eventId,
            oldClaim.claimToken(),
            OutboxDeliveryResult.failed(true, OutboxErrorType.NETWORK, null),
            NOW.plusSeconds(1)));
    assertEquals(0, retryCount(eventId));
    assertTrue(outboxStore.publish(eventId, currentClaim.claimToken(), NOW.plusSeconds(2)));
    assertEquals("PUBLISHED", status(eventId));
  }

  @Test
  @DisplayName("CT-OUTBOX-001 두 워커가 SKIP LOCKED로 겹치지 않게 배치를 나눈다")
  void concurrentWorkersClaimDisjointBatches() throws Exception {
    List<String> expected = new ArrayList<>();
    for (int index = 0; index < 6; index++) {
      expected.add(
          insertPendingEvent(0, NOW.minusSeconds(1), NOW.minusSeconds(10 - index), null, null));
    }
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<List<OutboxClaim>> first =
          executor.submit(
              () -> {
                assertTrue(start.await(2, TimeUnit.SECONDS));
                return outboxStore.claim(NOW, 3);
              });
      Future<List<OutboxClaim>> second =
          executor.submit(
              () -> {
                assertTrue(start.await(2, TimeUnit.SECONDS));
                return outboxStore.claim(NOW, 3);
              });
      start.countDown();

      Set<String> firstIds = new HashSet<>(eventIds(first.get(5, TimeUnit.SECONDS)));
      Set<String> secondIds = new HashSet<>(eventIds(second.get(5, TimeUnit.SECONDS)));
      Set<String> union = new HashSet<>(firstIds);
      union.addAll(secondIds);

      assertEquals(3, firstIds.size());
      assertEquals(3, secondIds.size());
      assertTrue(firstIds.stream().noneMatch(secondIds::contains));
      assertEquals(Set.copyOf(expected), union);
      assertEquals(
          6L,
          count(
              "SELECT COUNT(*) FROM outbox_events WHERE status = 'PROCESSING' AND order_id IN "
                  + orderIds()));
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  @DisplayName("IT-OUTBOX-004 50건을 잠금 조회 1회와 set-based 갱신 1회로 선점한다")
  void claimsFiftyRowsInTwoDatabaseStatements() {
    List<String> expected = new ArrayList<>();
    for (int index = 0; index < 50; index++) {
      expected.add(
          insertPendingEvent(0, NOW.minusSeconds(1), NOW.minusSeconds(100 - index), null, null));
    }
    SqlStatementCounter statementCounter = new SqlStatementCounter();
    DataSource measuredDataSource = measuredDataSource(statementCounter);
    JdbcTemplate measuredJdbcTemplate = new JdbcTemplate(measuredDataSource);
    OutboxStore measuredStore = new OutboxStore(measuredJdbcTemplate);
    TransactionTemplate transactionTemplate =
        new TransactionTemplate(new DataSourceTransactionManager(measuredDataSource));

    List<OutboxClaim> claims = transactionTemplate.execute(status -> measuredStore.claim(NOW, 50));

    assertEquals(expected, eventIds(claims));
    Set<String> claimTokens = new HashSet<>(claims.stream().map(OutboxClaim::claimToken).toList());
    Set<String> storedClaimTokens =
        new HashSet<>(
            jdbcTemplate.queryForList(
                "SELECT claim_token FROM outbox_events WHERE order_id IN " + orderIds(),
                String.class));
    assertEquals(50, claimTokens.size());
    assertEquals(claimTokens, storedClaimTokens);
    assertEquals(2L, statementCounter.preparedStatements.get());
    assertEquals(0L, statementCounter.batchExecutions.get());
    assertEquals(1L, statementCounter.individualUpdateExecutions.get());
    assertEquals(
        50L,
        count(
            "SELECT COUNT(*) FROM outbox_events WHERE status = 'PROCESSING' AND order_id IN "
                + orderIds()));
  }

  private String insertPendingEvent(
      int retryCount,
      Instant nextRetryAt,
      Instant createdAt,
      Integer lastHttpStatus,
      OutboxErrorType lastErrorType) {
    long orderId = insertOrder(createdAt);
    String eventId = UUID.randomUUID().toString();
    jdbcTemplate.update(
        "INSERT INTO outbox_events "
            + "(event_id, order_id, event_type, payload, status, retry_count, next_retry_at, "
            + "last_http_status, last_error_type, created_at) "
            + "VALUES (?, ?, 'ORDER_PAID', JSON_OBJECT('eventId', ?), 'PENDING', ?, ?, ?, ?, ?)",
        eventId,
        orderId,
        eventId,
        retryCount,
        Timestamp.from(nextRetryAt),
        lastHttpStatus,
        lastErrorType == null ? null : lastErrorType.name(),
        Timestamp.from(createdAt));
    return eventId;
  }

  private String insertProcessingEvent(Instant nextRetryAt, Instant lockedAt, Instant createdAt) {
    long orderId = insertOrder(createdAt);
    String eventId = UUID.randomUUID().toString();
    jdbcTemplate.update(
        "INSERT INTO outbox_events "
            + "(event_id, order_id, event_type, payload, status, retry_count, next_retry_at, "
            + "locked_at, claim_token, created_at) "
            + "VALUES (?, ?, 'ORDER_PAID', JSON_OBJECT('eventId', ?), 'PROCESSING', 0, ?, ?, ?, ?)",
        eventId,
        orderId,
        eventId,
        Timestamp.from(nextRetryAt),
        Timestamp.from(lockedAt),
        UUID.randomUUID().toString(),
        Timestamp.from(createdAt));
    return eventId;
  }

  private long insertOrder(Instant createdAt) {
    long orderId = ID_SEQUENCE.incrementAndGet();
    Timestamp timestamp = Timestamp.from(createdAt);
    jdbcTemplate.update(
        "INSERT INTO orders "
            + "(id, user_id, menu_id, menu_name_snapshot, paid_amount, status, paid_at, created_at) "
            + "VALUES (?, ?, ?, 'Outbox 메뉴', 4000, 'PAID', ?, ?)",
        orderId,
        userId,
        menuId,
        timestamp,
        timestamp);
    return orderId;
  }

  private List<String> eventIds(List<OutboxClaim> claims) {
    return claims.stream().map(OutboxClaim::eventId).toList();
  }

  private String status(String eventId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM outbox_events WHERE event_id = ?", String.class, eventId);
  }

  private int retryCount(String eventId) {
    Integer value =
        jdbcTemplate.queryForObject(
            "SELECT retry_count FROM outbox_events WHERE event_id = ?", Integer.class, eventId);
    return value == null ? -1 : value;
  }

  private String lifecycle(String eventId) {
    return jdbcTemplate.queryForObject(
        "SELECT CONCAT(status, '|', retry_count, '|', COALESCE(DATE_FORMAT(next_retry_at, "
            + "'%Y-%m-%d %H:%i:%s.%f'), 'null'), '|', COALESCE(last_http_status, 'null'), '|', "
            + "COALESCE(last_error_type, 'null')) FROM outbox_events WHERE event_id = ?",
        String.class, eventId);
  }

  private String orderIds() {
    return "(SELECT id FROM orders WHERE user_id = " + userId + ")";
  }

  private long count(String sql) {
    Long value = jdbcTemplate.queryForObject(sql, Long.class);
    return value == null ? 0 : value;
  }

  private DataSource measuredDataSource(SqlStatementCounter statementCounter) {
    DataSource delegate = jdbcTemplate.getDataSource();
    if (delegate == null) {
      throw new IllegalStateException("테스트 DataSource가 필요합니다.");
    }
    return (DataSource)
        Proxy.newProxyInstance(
            DataSource.class.getClassLoader(),
            new Class<?>[] {DataSource.class},
            (proxy, method, arguments) -> {
              Object result = invoke(method, delegate, arguments);
              if (method.getName().equals("getConnection")
                  && result instanceof java.sql.Connection connection) {
                return measuredConnection(connection, statementCounter);
              }
              return result;
            });
  }

  private java.sql.Connection measuredConnection(
      java.sql.Connection delegate, SqlStatementCounter statementCounter) {
    return (java.sql.Connection)
        Proxy.newProxyInstance(
            java.sql.Connection.class.getClassLoader(),
            new Class<?>[] {java.sql.Connection.class},
            (proxy, method, arguments) -> {
              Object result = invoke(method, delegate, arguments);
              if (method.getName().equals("prepareStatement")
                  && result instanceof java.sql.PreparedStatement preparedStatement) {
                statementCounter.preparedStatements.incrementAndGet();
                return measuredStatement(preparedStatement, statementCounter);
              }
              return result;
            });
  }

  private java.sql.PreparedStatement measuredStatement(
      java.sql.PreparedStatement delegate, SqlStatementCounter statementCounter) {
    return (java.sql.PreparedStatement)
        Proxy.newProxyInstance(
            java.sql.PreparedStatement.class.getClassLoader(),
            new Class<?>[] {java.sql.PreparedStatement.class},
            (proxy, method, arguments) -> {
              if (method.getName().equals("executeBatch")) {
                statementCounter.batchExecutions.incrementAndGet();
              }
              if (method.getName().equals("executeUpdate")) {
                statementCounter.individualUpdateExecutions.incrementAndGet();
              }
              return invoke(method, delegate, arguments);
            });
  }

  private Object invoke(Method method, Object target, Object[] arguments) throws Throwable {
    try {
      return method.invoke(target, arguments);
    } catch (InvocationTargetException exception) {
      throw exception.getTargetException();
    }
  }

  private static final class SqlStatementCounter {

    private final AtomicLong preparedStatements = new AtomicLong();
    private final AtomicLong batchExecutions = new AtomicLong();
    private final AtomicLong individualUpdateExecutions = new AtomicLong();
  }
}
