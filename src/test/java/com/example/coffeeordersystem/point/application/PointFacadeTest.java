package com.example.coffeeordersystem.point.application;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.coffeeordersystem.common.observability.BusinessEventLogger;
import com.example.coffeeordersystem.idempotency.application.IdempotencyClaim;
import com.example.coffeeordersystem.idempotency.application.IdempotencyFacade;
import com.example.coffeeordersystem.idempotency.application.IdempotencyOperation;
import com.example.coffeeordersystem.idempotency.application.IdempotencyResponseCodec;
import com.example.coffeeordersystem.idempotency.application.RequestHasher;
import com.example.coffeeordersystem.point.domain.PointAccount;
import com.example.coffeeordersystem.point.infrastructure.PointAccountRepository;
import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PointFacadeTest {

  private static final String IDEMPOTENCY_KEY = "11111111-1111-4111-8111-111111111111";

  @Test
  @DisplayName("CT-POINT-001 사용자 락을 얻은 뒤 충전 시각을 확정한다")
  void capturesChargeTimeAfterUserLock() throws Exception {
    PointAccountRepository pointAccountRepository = mock(PointAccountRepository.class);
    IdempotencyFacade idempotencyFacade = mock(IdempotencyFacade.class);
    RequestHasher requestHasher = mock(RequestHasher.class);
    IdempotencyResponseCodec responseCodec = mock(IdempotencyResponseCodec.class);
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
    when(idempotencyFacade.claim(
            1L, IdempotencyOperation.CHARGE, IDEMPOTENCY_KEY, "request-hash", chargedAt))
        .thenReturn(new IdempotencyClaim(3L, "request-hash", "PROCESSING", null, null));
    when(account.pointBalance()).thenReturn(200L);
    when(responseCodec.encodeSuccess(anyString(), anyString(), any())).thenReturn("response-body");

    PointFacade pointFacade =
        new PointFacade(
            pointAccountRepository,
            idempotencyFacade,
            requestHasher,
            responseCodec,
            clock,
            businessEventLogger);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      var result =
          executor.submit(
              () ->
                  pointFacade.charge(
                      ChargeCommand.from(1L, BigInteger.valueOf(100L), IDEMPOTENCY_KEY)));

      assertTrue(lockAttempted.await(2, TimeUnit.SECONDS));
      verifyNoInteractions(clock);
      releaseLock.countDown();
      result.get(2, TimeUnit.SECONDS);

      verify(clock).instant();
      verify(account).charge(100L, chargedAt);
      verify(idempotencyFacade).complete(3L, 200, "POINT_CHARGED", "response-body", chargedAt);
    } finally {
      releaseLock.countDown();
      executor.shutdownNow();
    }
  }
}
