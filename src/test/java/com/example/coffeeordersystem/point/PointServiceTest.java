package com.example.coffeeordersystem.point;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.coffeeordersystem.common.observability.BusinessEventLogger;
import com.example.coffeeordersystem.idempotency.IdempotencyClaim;
import com.example.coffeeordersystem.idempotency.IdempotencyOperation;
import com.example.coffeeordersystem.idempotency.IdempotencyService;
import com.example.coffeeordersystem.idempotency.RequestHasher;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class PointServiceTest {

  @Test
  @DisplayName("CT-POINT-001 사용자 락을 얻은 뒤 충전 시각을 확정한다")
  void capturesChargeTimeAfterUserLock() throws Exception {
    PointAccountRepository pointAccountRepository = mock(PointAccountRepository.class);
    IdempotencyService idempotencyService = mock(IdempotencyService.class);
    RequestHasher requestHasher = mock(RequestHasher.class);
    ObjectMapper objectMapper = mock(ObjectMapper.class);
    Clock clock = mock(Clock.class);
    BusinessEventLogger businessEventLogger = mock(BusinessEventLogger.class);
    PointAccount account = mock(PointAccount.class);
    CountDownLatch lockAttempted = new CountDownLatch(1);
    CountDownLatch releaseLock = new CountDownLatch(1);
    Instant chargedAt = Instant.parse("2026-07-16T06:00:00Z");

    when(pointAccountRepository.findByIdForUpdate(1L))
        .thenAnswer(
            invocation -> {
              lockAttempted.countDown();
              assertTrue(releaseLock.await(2, TimeUnit.SECONDS));
              return Optional.of(account);
            });
    when(clock.instant()).thenReturn(chargedAt);
    when(requestHasher.hash(IdempotencyOperation.CHARGE, 100L)).thenReturn("request-hash");
    when(idempotencyService.claim(
            1L, IdempotencyOperation.CHARGE, "idempotency-key", "request-hash", chargedAt))
        .thenReturn(new IdempotencyClaim(3L, "request-hash", "PROCESSING", null, null));
    when(account.pointBalance()).thenReturn(200L);
    JsonNode responseBody = mock(JsonNode.class);
    doReturn(responseBody).when(objectMapper).valueToTree(any());

    PointService pointService =
        new PointService(
            pointAccountRepository,
            idempotencyService,
            requestHasher,
            objectMapper,
            clock,
            businessEventLogger);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      var result =
          executor.submit(
              () -> pointService.charge(new ChargeCommand(1L, 100L, "idempotency-key")));

      assertTrue(lockAttempted.await(2, TimeUnit.SECONDS));
      verifyNoInteractions(clock);
      releaseLock.countDown();
      result.get(2, TimeUnit.SECONDS);

      verify(clock).instant();
      verify(account).charge(100L, chargedAt);
      verify(idempotencyService).complete(3L, 200, "POINT_CHARGED", responseBody, chargedAt);
    } finally {
      releaseLock.countDown();
      executor.shutdownNow();
    }
  }
}
