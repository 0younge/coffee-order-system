package com.example.coffeeordersystem.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OutboxWorkerConfigurationTest {

  private final OutboxWorkerConfiguration configuration = new OutboxWorkerConfiguration();

  @Test
  @DisplayName("QT-CONFIG-001 외부 주소와 HTTP 클라이언트 설정을 엄격하게 검증한다")
  void validatesBaseUrlAndClientPolicy() {
    HttpClient client = configuration.outboxHttpClient();

    assertEquals(Duration.ofSeconds(2), client.connectTimeout().orElseThrow());
    assertEquals(HttpClient.Redirect.NEVER, client.followRedirects());
    assertEquals(
        "http://localhost:8081/base",
        configuration.collectionApiBaseUri("http://localhost:8081/base").toString());
    assertThrows(
        IllegalStateException.class, () -> configuration.collectionApiBaseUri("not-a-url"));
    assertThrows(
        IllegalStateException.class,
        () -> configuration.collectionApiBaseUri("ftp://localhost/events"));
    assertThrows(
        IllegalStateException.class,
        () -> configuration.collectionApiBaseUri("https://user:password@example.com"));
  }

  @Test
  @DisplayName("QT-CONFIG-001 워커 활성화 시 외부 주소 누락·오류로 시작에 실패한다")
  void requiresValidBaseUrlOnlyWhenWorkerIsEnabled() {
    ApplicationContextRunner runner =
        new ApplicationContextRunner().withUserConfiguration(OutboxWorkerConfiguration.class);

    runner.run(context -> assertNotNull(context.getStartupFailure()));
    runner
        .withPropertyValues("COLLECTION_API_BASE_URL=ftp://localhost")
        .run(context -> assertNotNull(context.getStartupFailure()));
    runner
        .withPropertyValues("outbox.worker.enabled=false")
        .run(context -> assertNull(context.getStartupFailure()));
  }
}
