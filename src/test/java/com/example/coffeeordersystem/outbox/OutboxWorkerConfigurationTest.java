package com.example.coffeeordersystem.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OutboxWorkerConfigurationTest {

  private final OutboxWorkerConfiguration configuration = new OutboxWorkerConfiguration();

  @Test
  @DisplayName("UT-OUTBOX-003 QT-CONFIG-001 HTTP 클라이언트와 외부 주소를 검증한다")
  void validatesBaseUrlAndClientPolicy() {
    HttpClient client = configuration.outboxHttpClient();

    assertEquals(Duration.ofSeconds(2), client.connectTimeout().orElseThrow());
    assertEquals(HttpClient.Redirect.NEVER, client.followRedirects());
    assertEquals(
        "http://localhost:8081/base",
        configuration.collectionApiBaseUri("http://localhost:8081/base").toString());
    assertEquals(
        "https://localhost/base",
        configuration.collectionApiBaseUri("https://localhost/base").toString());
    assertEquals(
        "http://localhost:1/base",
        configuration.collectionApiBaseUri("http://localhost:1/base").toString());
    assertEquals(
        "https://localhost:65535/base",
        configuration.collectionApiBaseUri("https://localhost:65535/base").toString());
    assertThrows(
        IllegalStateException.class, () -> configuration.collectionApiBaseUri("not-a-url"));
    assertThrows(
        IllegalStateException.class,
        () -> configuration.collectionApiBaseUri("ftp://localhost/events"));
    assertThrows(
        IllegalStateException.class,
        () -> configuration.collectionApiBaseUri("https://user:password@example.com"));
    assertThrows(
        IllegalStateException.class,
        () -> configuration.collectionApiBaseUri("http://localhost:0"));
    assertThrows(
        IllegalStateException.class,
        () -> configuration.collectionApiBaseUri("http://localhost:65536"));
    assertThrows(
        IllegalStateException.class,
        () -> configuration.collectionApiBaseUri("http://localhost:99999"));
    assertThrows(
        IllegalStateException.class, () -> configuration.collectionApiBaseUri("http://localhost:"));
  }

  @Test
  @DisplayName("QT-CONFIG-004 폴링 간격은 1ms 이상 1000ms 이하만 허용한다")
  void validatesPollIntervalForFirstAttemptContract() {
    assertEquals(1, configuration.outboxWorkerSettings(1, 1).pollIntervalMillis());
    assertEquals(1000, configuration.outboxWorkerSettings(50, 1000).pollIntervalMillis());
    assertThrows(IllegalStateException.class, () -> configuration.outboxWorkerSettings(50, 0));
    assertThrows(IllegalStateException.class, () -> configuration.outboxWorkerSettings(50, 1001));
  }

  @Test
  @DisplayName("QT-CONFIG-001 워커 활성화 시 외부 주소 누락·오류로 시작에 실패한다")
  void requiresValidBaseUrlOnlyWhenWorkerIsEnabled() {
    ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withUserConfiguration(
                OutboxWorkerEnabledConfiguration.class, OutboxWorkerConfiguration.class);

    runner.run(context -> assertNotNull(context.getStartupFailure()));
    runner
        .withPropertyValues("COLLECTION_API_BASE_URL=ftp://localhost")
        .run(context -> assertNotNull(context.getStartupFailure()));
    runner
        .withPropertyValues("outbox.worker.enabled=tru")
        .run(context -> assertNotNull(context.getStartupFailure()));
    runner
        .withPropertyValues("outbox.worker.enabled=yes")
        .run(context -> assertNotNull(context.getStartupFailure()));
    new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withUserConfiguration(OutboxWorkerEnabledConfiguration.class)
        .withPropertyValues("OUTBOX_WORKER_ENABLED=tru")
        .run(context -> assertNotNull(context.getStartupFailure()));
    new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withUserConfiguration(OutboxWorkerConfiguration.class)
        .withPropertyValues(
            "COLLECTION_API_BASE_URL=http://localhost:8081", "OUTBOX_POLL_INTERVAL_MS=1001")
        .run(context -> assertNotNull(context.getStartupFailure()));
    runner
        .withPropertyValues("outbox.worker.enabled=false")
        .run(context -> assertNull(context.getStartupFailure()));
  }
}
