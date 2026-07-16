package com.example.coffeeordersystem.outbox;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = "outbox.worker",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
class OutboxHttpSender {

  private final HttpClient httpClient;
  private final URI endpoint;
  private final OutboxDeliveryClassifier classifier = new OutboxDeliveryClassifier();

  OutboxHttpSender(
      @Qualifier("outboxHttpClient") HttpClient httpClient,
      @Qualifier("collectionApiBaseUri") URI baseUri) {
    this.httpClient = httpClient;
    String baseUrl = baseUri.toString();
    this.endpoint = URI.create(baseUrl + (baseUrl.endsWith("/") ? "" : "/") + "events/orders");
  }

  CompletableFuture<OutboxDeliveryResult> send(String payload) {
    HttpRequest request =
        HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();
    return httpClient
        .sendAsync(request, HttpResponse.BodyHandlers.discarding())
        .handle(
            (response, throwable) ->
                throwable == null
                    ? classifier.classify(response.statusCode())
                    : classifier.classify(throwable));
  }
}
