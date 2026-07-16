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
    if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
        || uri.getHost() == null
        || uri.getUserInfo() != null
        || uri.getQuery() != null
        || uri.getFragment() != null) {
      throw new IllegalStateException("COLLECTION_API_BASE_URL은 유효한 HTTP(S) base URL이어야 합니다.");
    }
    return uri;
  }
}
