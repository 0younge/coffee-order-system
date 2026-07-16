package com.example.coffeeordersystem.outbox.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.coffeeordersystem.outbox.domain.OutboxDeliveryResult;
import com.example.coffeeordersystem.outbox.domain.OutboxErrorType;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutboxHttpSenderTest {

  private HttpServer server;
  private ExecutorService executor;
  private URI baseUri;

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    executor =
        Executors.newCachedThreadPool(
            runnable -> {
              Thread thread = new Thread(runnable, "outbox-mock-http");
              thread.setDaemon(true);
              return thread;
            });
    server.setExecutor(executor);
    server.start();
    baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/base");
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
    executor.shutdownNow();
  }

  @Test
  @DisplayName("EXT-OUTBOX-001 정확한 경로와 payload를 인증 헤더 없이 전송한다")
  void sendsExactContractWithoutAuthentication() throws Exception {
    AtomicReference<String> method = new AtomicReference<>();
    AtomicReference<String> body = new AtomicReference<>();
    AtomicReference<String> authorization = new AtomicReference<>();
    AtomicReference<String> contentType = new AtomicReference<>();
    server.createContext(
        "/base/events/orders",
        exchange -> {
          method.set(exchange.getRequestMethod());
          body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
          contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
          respond(exchange, 204);
        });
    String payload =
        "{\"eventId\":\"event-1\",\"eventType\":\"ORDER_PAID\",\"paymentAmount\":4000}";

    OutboxDeliveryResult result = sender().send(payload).get(2, TimeUnit.SECONDS);

    assertTrue(result.published());
    assertEquals("POST", method.get());
    assertEquals(payload, body.get());
    assertNull(authorization.get());
    assertEquals("application/json", contentType.get());
  }

  @Test
  @DisplayName("EXT-OUTBOX-001 base URL의 trailing slash와 경로를 보존한다")
  void preservesBasePathWithTrailingSlash() throws Exception {
    AtomicInteger requests = new AtomicInteger();
    server.createContext(
        "/base/events/orders",
        exchange -> {
          requests.incrementAndGet();
          respond(exchange, 204);
        });
    URI baseWithTrailingSlash = URI.create(baseUri + "/");

    OutboxDeliveryResult result = sender(baseWithTrailingSlash).send("{}").get(2, TimeUnit.SECONDS);

    assertTrue(result.published());
    assertEquals(1, requests.get());
  }

  @Test
  @DisplayName("EXT-OUTBOX-004 3xx를 따라가지 않고 영구 실패로 분류한다")
  void doesNotFollowRedirect() throws Exception {
    AtomicInteger redirectedRequests = new AtomicInteger();
    server.createContext(
        "/base/events/orders",
        exchange -> {
          exchange.getResponseHeaders().add("Location", "/redirected");
          respond(exchange, 302);
        });
    server.createContext(
        "/redirected",
        exchange -> {
          redirectedRequests.incrementAndGet();
          respond(exchange, 204);
        });

    OutboxDeliveryResult result = sender().send("{}").get(2, TimeUnit.SECONDS);

    assertFalse(result.published());
    assertFalse(result.retryable());
    assertEquals(OutboxErrorType.HTTP_3XX, result.errorType());
    assertEquals(302, result.httpStatus());
    assertEquals(0, redirectedRequests.get());
  }

  @Test
  @DisplayName("EXT-OUTBOX-003 EXT-OUTBOX-004 HTTP 실패를 정책대로 분류한다")
  void classifiesMockApiResponses() throws Exception {
    AtomicInteger status = new AtomicInteger(408);
    server.createContext("/base/events/orders", exchange -> respond(exchange, status.get()));
    OutboxHttpSender sender = sender();

    assertTrue(sender.send("{}").get(2, TimeUnit.SECONDS).retryable());
    status.set(429);
    assertTrue(sender.send("{}").get(2, TimeUnit.SECONDS).retryable());
    status.set(503);
    assertTrue(sender.send("{}").get(2, TimeUnit.SECONDS).retryable());
    status.set(422);
    assertFalse(sender.send("{}").get(2, TimeUnit.SECONDS).retryable());
  }

  @Test
  @DisplayName("UT-OUTBOX-003 EXT-OUTBOX-003 요청 전체 timeout은 5초다")
  void timesOutWholeRequest() throws Exception {
    server.createContext(
        "/base/events/orders",
        exchange -> {
          try {
            Thread.sleep(Duration.ofSeconds(6).toMillis());
            respond(exchange, 204);
          } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
          }
        });

    long startedAt = System.nanoTime();
    OutboxDeliveryResult result = sender().send("{}").get(7, TimeUnit.SECONDS);
    long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

    assertEquals(OutboxErrorType.TIMEOUT, result.errorType());
    assertTrue(result.retryable());
    assertTrue(elapsedMillis >= 4_500 && elapsedMillis < 7_000, "실행 시간: " + elapsedMillis);
  }

  @Test
  @DisplayName("EXT-OUTBOX-003 네트워크 연결 실패는 HTTP 상태 없이 재시도한다")
  void retriesNetworkFailure() throws Exception {
    OutboxHttpSender sender = sender();
    server.stop(0);

    OutboxDeliveryResult result = sender.send("{}").get(3, TimeUnit.SECONDS);

    assertEquals(OutboxErrorType.NETWORK, result.errorType());
    assertTrue(result.retryable());
    assertNull(result.httpStatus());
  }

  private OutboxHttpSender sender() {
    return sender(baseUri);
  }

  private OutboxHttpSender sender(URI targetBaseUri) {
    HttpClient client =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    return new OutboxHttpSender(client, targetBaseUri);
  }

  private void respond(HttpExchange exchange, int status) throws IOException {
    exchange.sendResponseHeaders(status, -1);
    exchange.close();
  }
}
