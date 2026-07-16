package com.example.coffeeordersystem.outbox;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class OutboxWorkerTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-16T00:00:00Z"), ZoneOffset.UTC);

  private final OutboxStore outboxStore = mock(OutboxStore.class);
  private final OutboxHttpSender httpSender = mock(OutboxHttpSender.class);
  private final OutboxMetrics metrics = mock(OutboxMetrics.class);
  private final OutboxWorker worker =
      new OutboxWorker(outboxStore, httpSender, CLOCK, metrics, new OutboxWorkerSettings(1, 1000));

  @Test
  @DisplayName("QT-OBS-001 Outbox 선점 예외의 메시지와 내부 정보를 로그에 노출하지 않는다")
  void hidesClaimExceptionDetails(CapturedOutput output) {
    when(outboxStore.claim(CLOCK.instant(), 1))
        .thenThrow(new IllegalStateException("SQL error DB_PASSWORD=top-secret"));

    worker.poll();

    assertTrue(output.getAll().contains("errorType=\"IllegalStateException\""));
    assertFalse(output.getAll().contains("SQL error"));
    assertFalse(output.getAll().contains("top-secret"));
  }

  @Test
  @DisplayName("QT-OBS-001 Outbox 결과 반영 예외의 메시지와 내부 정보를 로그에 노출하지 않는다")
  void hidesResultUpdateExceptionDetails(CapturedOutput output) {
    OutboxClaim claim = new OutboxClaim("event-1", "claim-1", "{}", 0);
    when(outboxStore.claim(CLOCK.instant(), 1)).thenReturn(List.of(claim));
    when(httpSender.send("{}"))
        .thenReturn(CompletableFuture.completedFuture(OutboxDeliveryResult.success()));
    when(outboxStore.publish("event-1", "claim-1", CLOCK.instant()))
        .thenThrow(new IllegalStateException("SQL error DB_PASSWORD=top-secret"));

    worker.poll();

    String failureLog =
        output
            .getAll()
            .lines()
            .filter(line -> line.contains("Outbox 이벤트 결과 반영 실패"))
            .findFirst()
            .orElseThrow();
    assertTrue(failureLog.contains("errorType=\"IllegalStateException\""));
    assertTrue(failureLog.contains("eventId=\"event-1\""));
    assertTrue(failureLog.contains("attempt=\"1\""));
    assertTrue(failureLog.contains("target=\"/events/orders\""));
    assertTrue(failureLog.contains("latencyMs="));
    assertTrue(failureLog.contains("result=\"state_update_failed\""));
    assertFalse(output.getAll().contains("SQL error"));
    assertFalse(output.getAll().contains("top-secret"));
  }

  @Test
  @DisplayName("UT-OUTBOX-004 전송 시작 예외 뒤 active 상태를 해제해 다음 poll을 허용한다")
  void releasesActiveStateAfterSynchronousSendFailure(CapturedOutput output) {
    OutboxClaim failedClaim = new OutboxClaim("event-1", "claim-1", "{}", 0);
    OutboxClaim nextClaim = new OutboxClaim("event-2", "claim-2", "{}", 0);
    when(outboxStore.claim(CLOCK.instant(), 1))
        .thenReturn(List.of(failedClaim))
        .thenReturn(List.of(nextClaim));
    when(httpSender.send("{}"))
        .thenThrow(new IllegalStateException("HTTP_CLIENT_SECRET=top-secret"))
        .thenReturn(CompletableFuture.completedFuture(OutboxDeliveryResult.success()));
    when(outboxStore.publish("event-2", "claim-2", CLOCK.instant())).thenReturn(true);

    assertDoesNotThrow(worker::poll);
    assertDoesNotThrow(worker::poll);

    verify(outboxStore, times(2)).claim(CLOCK.instant(), 1);
    verify(outboxStore).publish("event-2", "claim-2", CLOCK.instant());
    assertTrue(output.getAll().contains("errorType=\"IllegalStateException\""));
    assertFalse(output.getAll().contains("HTTP_CLIENT_SECRET"));
    assertFalse(output.getAll().contains("top-secret"));
  }
}
