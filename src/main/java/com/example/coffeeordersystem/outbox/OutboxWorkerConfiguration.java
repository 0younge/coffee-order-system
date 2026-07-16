package com.example.coffeeordersystem.outbox;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
    prefix = "outbox.worker",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
class OutboxWorkerConfiguration {

  @Bean("outboxHttpClient")
  HttpClient outboxHttpClient() {
    return HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
  }

  @Bean("collectionApiBaseUri")
  URI collectionApiBaseUri(@Value("${COLLECTION_API_BASE_URL}") String rawBaseUrl) {
    URI uri;
    try {
      uri = URI.create(rawBaseUrl);
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException("COLLECTION_API_BASE_URL이 올바른 URI가 아닙니다.", exception);
    }
    int port = uri.getPort();
    boolean invalidPort = port != -1 && (port < 1 || port > 65_535);
    if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
        || uri.getHost() == null
        || invalidPort
        || uri.getUserInfo() != null
        || uri.getQuery() != null
        || uri.getFragment() != null) {
      throw new IllegalStateException("COLLECTION_API_BASE_URL은 유효한 HTTP(S) base URL이어야 합니다.");
    }
    return uri;
  }

  @Bean
  OutboxWorkerSettings outboxWorkerSettings(
      @Value("${outbox.worker.batch-size:50}") int batchSize,
      @Value("${outbox.worker.poll-interval-ms:1000}") long pollIntervalMillis) {
    return new OutboxWorkerSettings(batchSize, pollIntervalMillis);
  }
}

record OutboxWorkerSettings(int batchSize, long pollIntervalMillis) {

  OutboxWorkerSettings {
    if (batchSize <= 0) {
      throw new IllegalStateException("OUTBOX_BATCH_SIZE는 1 이상이어야 합니다.");
    }
    if (pollIntervalMillis <= 0 || pollIntervalMillis > 1_000) {
      throw new IllegalStateException("OUTBOX_POLL_INTERVAL_MS는 1 이상 1000 이하여야 합니다.");
    }
  }
}
