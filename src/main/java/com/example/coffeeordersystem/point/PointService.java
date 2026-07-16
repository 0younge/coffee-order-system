package com.example.coffeeordersystem.point;

import com.example.coffeeordersystem.common.api.ApiResponse;
import com.example.coffeeordersystem.common.api.ApiResponseJsonCodec;
import com.example.coffeeordersystem.common.error.ApiException;
import com.example.coffeeordersystem.common.error.ErrorCode;
import com.example.coffeeordersystem.common.observability.BusinessEventLogger;
import com.example.coffeeordersystem.idempotency.application.IdempotencyClaim;
import com.example.coffeeordersystem.idempotency.application.IdempotencyFacade;
import com.example.coffeeordersystem.idempotency.application.IdempotencyOperation;
import com.example.coffeeordersystem.idempotency.application.RequestHasher;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
class PointService {

  private final PointAccountRepository pointAccountRepository;
  private final IdempotencyFacade idempotencyFacade;
  private final RequestHasher requestHasher;
  private final ApiResponseJsonCodec responseJsonCodec;
  private final Clock clock;
  private final BusinessEventLogger businessEventLogger;

  @Transactional
  PointChargeResult charge(ChargeCommand command) {
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
      return new PointChargeResult(
          claim.httpStatus(), responseJsonCodec.read(claim.responseBody()), claim.responseBody());
    }

    try {
      account.charge(command.amount(), now);
    } catch (ArithmeticException exception) {
      PointChargeResult result =
          complete(
              claim,
              failure(ErrorCode.POINT_BALANCE_OVERFLOW),
              ErrorCode.POINT_BALANCE_OVERFLOW.name(),
              now);
      businessEventLogger.pointResult(command.userId(), ErrorCode.POINT_BALANCE_OVERFLOW.name());
      return result;
    }

    PointChargeResult result =
        success(
            "POINT_CHARGED",
            "포인트를 충전했습니다.",
            new PointChargeResponse(command.amount(), account.pointBalance()));
    result = complete(claim, result, "POINT_CHARGED", now);
    businessEventLogger.pointResult(command.userId(), "POINT_CHARGED");
    return result;
  }

  private PointChargeResult success(String code, String message, Object data) {
    return response(HttpStatus.OK.value(), ApiResponse.success(code, message, data));
  }

  private PointChargeResult failure(ErrorCode errorCode) {
    return response(
        errorCode.httpStatus().value(), ApiResponse.failure(errorCode.name(), errorCode.message()));
  }

  private PointChargeResult response(int httpStatus, Object response) {
    String responseBody = responseJsonCodec.write(response);
    return new PointChargeResult(httpStatus, responseJsonCodec.read(responseBody), responseBody);
  }

  private PointChargeResult complete(
      IdempotencyClaim claim, PointChargeResult result, String resultCode, Instant now) {
    String storedResponseBody =
        idempotencyFacade.complete(
            claim.recordId(), result.httpStatus(), resultCode, result.responseBody(), now);
    return new PointChargeResult(
        result.httpStatus(), responseJsonCodec.read(storedResponseBody), storedResponseBody);
  }
}
