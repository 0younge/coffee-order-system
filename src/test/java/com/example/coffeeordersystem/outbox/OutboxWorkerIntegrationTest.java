package com.example.coffeeordersystem.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {"outbox.worker.enabled=true", "outbox.worker.batch-size=50"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OutboxWorkerIntegrationTest {

  private static final AtomicLong ID_SEQUENCE = new AtomicLong(9_000_000_000L);
  private static final MockCollectionApi MOCK_API = MockCollectionApi.start();

  @Autowired private MockMvc mockMvc;

  @Autowired private OutboxEventWriter outboxEventWriter;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private TransactionTemplate transactionTemplate;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private MeterRegistry meterRegistry;

  @Autowired private OutboxStore outboxStore;

  private long userId;
  private long menuId;

  @DynamicPropertySource
  static void outboxProperties(DynamicPropertyRegistry registry) {
    registry.add("COLLECTION_API_BASE_URL", MOCK_API::baseUrl);
  }

  @BeforeEach
  void setUp() {
    MOCK_API.reset(0, List.of(204), false, true);
    userId = ID_SEQUENCE.addAndGet(1_000);
    menuId = userId + 1;
    Timestamp now = Timestamp.from(Instant.now());
    jdbcTemplate.update(
        "INSERT INTO users (id, point_balance, created_at, updated_at) VALUES (?, 100000, ?, ?)",
        userId,
        now,
        now);
    jdbcTemplate.update(
        "INSERT INTO menus (id, name, price, created_at) VALUES (?, '워커 메뉴', 4000, ?)",
        menuId,
        now);
  }

  @AfterEach
  void tearDown() throws Exception {
    MOCK_API.release();
    await(
        () ->
            count(
                    "SELECT COUNT(*) FROM outbox_events WHERE status = 'PROCESSING' "
                        + "AND order_id IN "
                        + orderIds())
                == 0,
        Duration.ofSeconds(6));
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
  @DisplayName("EXT-OUTBOX-001 EXT-OUTBOX-002 EXT-OUTBOX-008 주문과 분리해 2초 안에 전송한다")
  void sendsCommittedOrderWithoutWaitingForExternalResponse() throws Exception {
    double publishedBefore = counter("coffee.outbox.delivery.published");
    MOCK_API.reset(1, List.of(204), true, true);

    mockMvc
        .perform(
            post("/api/v1/orders")
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":" + userId + ",\"menuId\":" + menuId + "}"))
        .andExpect(status().isCreated());

    assertTrue(MOCK_API.awaitRequests(Duration.ofSeconds(2)));
    MockRequest request = MOCK_API.requests().get(0);
    String eventId = eventIdForUser();
    Instant committedAt = createdAt(eventId);
    long firstAttemptMillis = Duration.between(committedAt, request.startedAt()).toMillis();

    assertTrue(
        firstAttemptMillis >= 0 && firstAttemptMillis < 2_000, "최초 시도: " + firstAttemptMillis);
    assertEquals("POST", request.method());
    assertEquals("/events/orders", request.path());
    assertNull(request.authorization());
    assertEquals(eventId, objectMapper.readTree(request.body()).get("eventId").stringValue());
    assertEquals(
        "ORDER_PAID", objectMapper.readTree(request.body()).get("eventType").stringValue());
    assertEquals(
        committedAt,
        Instant.parse(objectMapper.readTree(request.body()).get("occurredAt").stringValue()));
    assertEquals(userId, objectMapper.readTree(request.body()).get("userId").longValue());
    assertEquals(menuId, objectMapper.readTree(request.body()).get("menuId").longValue());
    assertEquals(4_000L, objectMapper.readTree(request.body()).get("paymentAmount").longValue());
    assertEquals("PROCESSING", statusOf(eventId));

    MOCK_API.release();
    await(() -> "PUBLISHED".equals(statusOf(eventId)), Duration.ofSeconds(2));
    assertEquals(publishedBefore + 1, counter("coffee.outbox.delivery.published"));
  }

  @Test
  @DisplayName("EXT-OUTBOX-002 인스턴스마다 활성 배치를 하나만 유지한다")
  void keepsOnlyOneActiveBatchPerInstance() throws Exception {
    MOCK_API.reset(1, List.of(204, 204), true, true);
    String firstEventId = insertEventsInOneTransaction(1).get(0);
    assertTrue(MOCK_API.awaitRequests(Duration.ofSeconds(2)));

    String secondEventId = insertEventsInOneTransaction(1).get(0);

    assertTrue(MOCK_API.hasRequestCountFor(1, Duration.ofMillis(1_300)));
    assertEquals("PROCESSING", statusOf(firstEventId));
    assertEquals("PENDING", statusOf(secondEventId));

    MOCK_API.release();
    assertTrue(MOCK_API.awaitRequestCount(2, Duration.ofSeconds(2)));
    await(() -> "PUBLISHED".equals(statusOf(firstEventId)), Duration.ofSeconds(2));
    await(() -> "PUBLISHED".equals(statusOf(secondEventId)), Duration.ofSeconds(2));
  }

  @Test
  @DisplayName("EXT-OUTBOX-002 활성 배치의 HTTP 요청을 병렬로 시작한다")
  void sendsClaimedBatchInParallel() throws Exception {
    MOCK_API.reset(3, List.of(204, 204, 204), true, true);
    List<String> eventIds = insertEventsInOneTransaction(3);

    assertTrue(MOCK_API.awaitRequests(Duration.ofSeconds(2)));
    List<Instant> started =
        MOCK_API.requests().stream().map(MockRequest::startedAt).sorted().toList();
    assertTrue(
        Duration.between(started.get(0), started.get(2)).toMillis() < 1_000,
        "병렬 요청 시작 시각: " + started);
    assertEquals(
        Set.copyOf(eventIds),
        MOCK_API.requests().stream()
            .map(request -> eventId(request.body()))
            .collect(java.util.stream.Collectors.toSet()));

    MOCK_API.release();
    await(
        () ->
            count(
                    "SELECT COUNT(*) FROM outbox_events WHERE status = 'PUBLISHED' "
                        + "AND event_id IN ("
                        + quoted(eventIds)
                        + ")")
                == 3,
        Duration.ofSeconds(2));
  }

  @Test
  @DisplayName("EXT-OUTBOX-003 EXT-OUTBOX-006 EXT-OUTBOX-009 같은 eventId 재시도 후 성공한다")
  void retriesSameEventAndClearsFailureFieldsAfterSuccess() throws Exception {
    double retriedBefore = counter("coffee.outbox.delivery.retried");
    double publishedBefore = counter("coffee.outbox.delivery.published");
    MOCK_API.reset(1, List.of(503), false, true);
    String eventId = insertEventsInOneTransaction(1).get(0);

    assertTrue(MOCK_API.awaitRequests(Duration.ofSeconds(2)));
    await(
        () -> "PENDING".equals(statusOf(eventId)) && retryCount(eventId) == 1,
        Duration.ofSeconds(2));
    assertEquals("HTTP_5XX", lastErrorType(eventId));
    assertEquals(503, lastHttpStatus(eventId));
    MOCK_API.reset(1, List.of(204), false, false);
    jdbcTemplate.update(
        "UPDATE outbox_events SET next_retry_at = UTC_TIMESTAMP(6) WHERE event_id = ?", eventId);

    assertTrue(MOCK_API.awaitRequests(Duration.ofSeconds(2)));
    await(() -> "PUBLISHED".equals(statusOf(eventId)), Duration.ofSeconds(2));

    assertEquals(2, MOCK_API.requests().size());
    assertTrue(
        MOCK_API.requests().stream().allMatch(request -> eventId.equals(eventId(request.body()))));
    assertEquals(1, retryCount(eventId));
    assertEquals(retriedBefore + 1, counter("coffee.outbox.delivery.retried"));
    assertEquals(publishedBefore + 1, counter("coffee.outbox.delivery.published"));
    assertEquals(
        1L,
        count(
            "SELECT COUNT(*) FROM outbox_events WHERE event_id = '"
                + eventId
                + "' AND status = 'PUBLISHED' AND next_retry_at IS NULL "
                + "AND locked_at IS NULL AND claim_token IS NULL AND published_at IS NOT NULL "
                + "AND failed_at IS NULL AND last_http_status IS NULL AND last_error_type IS NULL"));
  }

  @Test
  @DisplayName("QT-OBS-002 EXT-OUTBOX-004 영구 실패 counter를 증가시킨다")
  void recordsPermanentFailureMetric() throws Exception {
    double failedBefore = counter("coffee.outbox.delivery.failed");
    MOCK_API.reset(1, List.of(422), false, true);
    String eventId = insertEventsInOneTransaction(1).get(0);

    assertTrue(MOCK_API.awaitRequests(Duration.ofSeconds(2)));
    await(() -> "FAILED".equals(statusOf(eventId)), Duration.ofSeconds(2));

    assertEquals(failedBefore + 1, counter("coffee.outbox.delivery.failed"));
  }

  @Test
  @DisplayName("QT-OBS-002 EXT-OUTBOX-007 stale claim의 fencing counter를 증가시킨다")
  void recordsFencingRejectionMetric() throws Exception {
    double rejectedBefore = counter("coffee.outbox.fencing.rejected");
    MOCK_API.reset(1, List.of(204), true, true);
    String eventId = insertEventsInOneTransaction(1).get(0);
    assertTrue(MOCK_API.awaitRequests(Duration.ofSeconds(2)));
    jdbcTemplate.update(
        "UPDATE outbox_events SET locked_at = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 31 SECOND) "
            + "WHERE event_id = ?",
        eventId);
    OutboxClaim currentClaim = outboxStore.claim(Instant.now(), 1).get(0);

    MOCK_API.release();
    await(
        () -> counter("coffee.outbox.fencing.rejected") == rejectedBefore + 1,
        Duration.ofSeconds(2));

    assertEquals("PROCESSING", statusOf(eventId));
    assertTrue(outboxStore.publish(eventId, currentClaim.claimToken(), Instant.now()));
  }

  private List<String> insertEventsInOneTransaction(int count) {
    List<String> eventIds = new ArrayList<>();
    transactionTemplate.executeWithoutResult(
        status -> {
          for (int index = 0; index < count; index++) {
            long orderId = ID_SEQUENCE.incrementAndGet();
            Instant occurredAt = Instant.now();
            Timestamp timestamp = Timestamp.from(occurredAt);
            jdbcTemplate.update(
                "INSERT INTO orders "
                    + "(id, user_id, menu_id, menu_name_snapshot, paid_amount, status, paid_at, "
                    + "created_at) VALUES (?, ?, ?, '워커 메뉴', 4000, 'PAID', ?, ?)",
                orderId,
                userId,
                menuId,
                timestamp,
                timestamp);
            eventIds.add(
                outboxEventWriter.appendOrderPaid(orderId, userId, menuId, 4_000L, occurredAt));
          }
        });
    return List.copyOf(eventIds);
  }

  private String eventId(String body) {
    try {
      return objectMapper.readTree(body).get("eventId").stringValue();
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  private String eventIdForUser() {
    return jdbcTemplate.queryForObject(
        "SELECT event_id FROM outbox_events WHERE order_id IN "
            + "(SELECT id FROM orders WHERE user_id = ?)",
        String.class,
        userId);
  }

  private Instant createdAt(String eventId) {
    return jdbcTemplate
        .queryForObject(
            "SELECT created_at FROM outbox_events WHERE event_id = ?", Timestamp.class, eventId)
        .toInstant();
  }

  private String statusOf(String eventId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM outbox_events WHERE event_id = ?", String.class, eventId);
  }

  private int retryCount(String eventId) {
    return jdbcTemplate.queryForObject(
        "SELECT retry_count FROM outbox_events WHERE event_id = ?", Integer.class, eventId);
  }

  private Integer lastHttpStatus(String eventId) {
    return jdbcTemplate.queryForObject(
        "SELECT last_http_status FROM outbox_events WHERE event_id = ?", Integer.class, eventId);
  }

  private String lastErrorType(String eventId) {
    return jdbcTemplate.queryForObject(
        "SELECT last_error_type FROM outbox_events WHERE event_id = ?", String.class, eventId);
  }

  private void await(BooleanSupplier condition, Duration timeout) throws Exception {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline) {
      if (condition.getAsBoolean()) {
        return;
      }
      Thread.sleep(20);
    }
    assertTrue(condition.getAsBoolean(), "제한 시간 안에 조건을 만족하지 못했습니다.");
  }

  private String orderIds() {
    return "(SELECT id FROM orders WHERE user_id = " + userId + ")";
  }

  private String quoted(List<String> values) {
    return values.stream()
        .map(value -> "'" + value + "'")
        .collect(java.util.stream.Collectors.joining(","));
  }

  private long count(String sql) {
    Long value = jdbcTemplate.queryForObject(sql, Long.class);
    return value == null ? 0 : value;
  }

  private double counter(String name) {
    return meterRegistry.get(name).counter().count();
  }

  private record MockRequest(
      Instant startedAt, String method, String path, String authorization, String body) {}

  private static final class MockCollectionApi {

    private final HttpServer server;
    private final ExecutorService executor;
    private final CopyOnWriteArrayList<MockRequest> requests = new CopyOnWriteArrayList<>();
    private final ConcurrentLinkedQueue<Integer> statuses = new ConcurrentLinkedQueue<>();
    private volatile CountDownLatch requestsLatch = new CountDownLatch(0);
    private volatile CountDownLatch releaseLatch = new CountDownLatch(0);

    private MockCollectionApi(HttpServer server, ExecutorService executor) {
      this.server = server;
      this.executor = executor;
    }

    static MockCollectionApi start() {
      try {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService executor =
            Executors.newCachedThreadPool(
                runnable -> {
                  Thread thread = new Thread(runnable, "outbox-worker-mock-http");
                  thread.setDaemon(true);
                  return thread;
                });
        MockCollectionApi api = new MockCollectionApi(server, executor);
        server.createContext("/events/orders", api::handle);
        server.setExecutor(executor);
        server.start();
        return api;
      } catch (IOException exception) {
        throw new ExceptionInInitializerError(exception);
      }
    }

    String baseUrl() {
      return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    void reset(int expectedRequests, List<Integer> responseStatuses, boolean block, boolean clear) {
      if (clear) {
        requests.clear();
      }
      statuses.clear();
      statuses.addAll(responseStatuses);
      requestsLatch = new CountDownLatch(expectedRequests);
      releaseLatch = new CountDownLatch(block ? 1 : 0);
    }

    boolean awaitRequests(Duration timeout) throws InterruptedException {
      return requestsLatch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    boolean awaitRequestCount(int expected, Duration timeout) throws InterruptedException {
      long deadline = System.nanoTime() + timeout.toNanos();
      while (System.nanoTime() < deadline) {
        if (requests.size() >= expected) {
          return true;
        }
        Thread.sleep(20);
      }
      return requests.size() >= expected;
    }

    boolean hasRequestCountFor(int expected, Duration duration) throws InterruptedException {
      long deadline = System.nanoTime() + duration.toNanos();
      while (System.nanoTime() < deadline) {
        if (requests.size() != expected) {
          return false;
        }
        Thread.sleep(20);
      }
      return requests.size() == expected;
    }

    void release() {
      releaseLatch.countDown();
    }

    List<MockRequest> requests() {
      return List.copyOf(requests);
    }

    private void handle(HttpExchange exchange) throws IOException {
      requests.add(
          new MockRequest(
              Instant.now(),
              exchange.getRequestMethod(),
              exchange.getRequestURI().getPath(),
              exchange.getRequestHeaders().getFirst("Authorization"),
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
      requestsLatch.countDown();
      try {
        releaseLatch.await(6, TimeUnit.SECONDS);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
      }
      Integer status = statuses.poll();
      exchange.sendResponseHeaders(status == null ? 204 : status, -1);
      exchange.close();
    }
  }
}
