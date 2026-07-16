package com.example.coffeeordersystem.point.application;

import com.example.coffeeordersystem.common.error.ApiException;
import com.example.coffeeordersystem.common.error.ErrorCode;
import com.example.coffeeordersystem.common.observability.BusinessEventLogger;
import com.example.coffeeordersystem.idempotency.application.IdempotencyClaim;
import com.example.coffeeordersystem.idempotency.application.IdempotencyFacade;
import com.example.coffeeordersystem.idempotency.application.IdempotencyOperation;
import com.example.coffeeordersystem.idempotency.application.IdempotencyResponseCodec;
import com.example.coffeeordersystem.idempotency.application.RequestHasher;
import com.example.coffeeordersystem.point.domain.PointAccount;
import com.example.coffeeordersystem.point.infrastructure.PointAccountRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Service
public class PointFacade {

  private final PointAccountRepository pointAccountRepository;
  private final IdempotencyFacade idempotencyFacade;
  private final RequestHasher requestHasher;
  private final IdempotencyResponseCodec responseCodec;
  private final Clock clock;
  private final BusinessEventLogger businessEventLogger;

  @Transactional
  public PointChargeResult charge(ChargeCommand command) {
    PointAccount account =
        pointAccountRepository
            .findByIdForUpdate(command.userId())
            .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    Instant now = clock.instant();

    String requestHash = requestHasher.hash(IdempotencyOperation.CHARGE, command.amount());
    IdempotencyClaim claim =
        idempotencyFacade.claim(
            command.userId(),
            IdempotencyOperation.CHARGE,
            command.idempotencyKey(),
            requestHash,
            now);
    if (claim.completed()) {
      if (!claim.requestHash().equals(requestHash)) {
        return failure(ErrorCode.IDEMPOTENCY_KEY_REUSED);
      }
      return new PointChargeResult(claim.httpStatus(), claim.responseBody());
    }

    try {
      account.charge(command.amount(), now);
    } catch (ArithmeticException exception) {
      PointChargeResult result = failure(ErrorCode.POINT_BALANCE_OVERFLOW);
      complete(claim, result, ErrorCode.POINT_BALANCE_OVERFLOW.name(), now);
      businessEventLogger.pointResult(command.userId(), ErrorCode.POINT_BALANCE_OVERFLOW.name());
      return result;
    }

    PointChargeResult result =
        success(
            "POINT_CHARGED",
            "포인트를 충전했습니다.",
            new PointChargeData(command.amount(), account.pointBalance()));
    complete(claim, result, "POINT_CHARGED", now);
    businessEventLogger.pointResult(command.userId(), "POINT_CHARGED");
    return result;
  }

  private PointChargeResult success(String code, String message, Object data) {
    return new PointChargeResult(200, responseCodec.encodeSuccess(code, message, data));
  }

  private PointChargeResult failure(ErrorCode errorCode) {
    return new PointChargeResult(
        errorCode.httpStatus().value(),
        responseCodec.encodeFailure(errorCode.name(), errorCode.message()));
  }

  private void complete(
      IdempotencyClaim claim, PointChargeResult result, String resultCode, Instant now) {
    idempotencyFacade.complete(
        claim.recordId(), result.httpStatus(), resultCode, result.responseBody(), now);
  }
}
