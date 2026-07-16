package com.example.coffeeordersystem.point;

import com.example.coffeeordersystem.common.api.ApiResponse;
import com.example.coffeeordersystem.common.error.ApiException;
import com.example.coffeeordersystem.common.error.ErrorCode;
import com.example.coffeeordersystem.common.observability.BusinessEventLogger;
import com.example.coffeeordersystem.idempotency.IdempotencyClaim;
import com.example.coffeeordersystem.idempotency.IdempotencyOperation;
import com.example.coffeeordersystem.idempotency.IdempotencyService;
import com.example.coffeeordersystem.idempotency.RequestHasher;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
class PointService {

  private final PointAccountRepository pointAccountRepository;
  private final IdempotencyService idempotencyService;
  private final RequestHasher requestHasher;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final BusinessEventLogger businessEventLogger;

  PointService(
      PointAccountRepository pointAccountRepository,
      IdempotencyService idempotencyService,
      RequestHasher requestHasher,
      ObjectMapper objectMapper,
      Clock clock,
      BusinessEventLogger businessEventLogger) {
    this.pointAccountRepository = pointAccountRepository;
    this.idempotencyService = idempotencyService;
    this.requestHasher = requestHasher;
    this.objectMapper = objectMapper;
    this.clock = clock;
    this.businessEventLogger = businessEventLogger;
  }

  @Transactional
  PointChargeResult charge(ChargeCommand command) {
    PointAccount account =
        pointAccountRepository
            .findByIdForUpdate(command.userId())
            .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    Instant now = clock.instant();

    String requestHash = requestHasher.hash(IdempotencyOperation.CHARGE, command.amount());
    IdempotencyClaim claim =
        idempotencyService.claim(
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
            new PointChargeResponse(command.amount(), account.pointBalance()));
    complete(claim, result, "POINT_CHARGED", now);
    businessEventLogger.pointResult(command.userId(), "POINT_CHARGED");
    return result;
  }

  private PointChargeResult success(String code, String message, Object data) {
    JsonNode body = objectMapper.valueToTree(ApiResponse.success(code, message, data));
    return new PointChargeResult(HttpStatus.OK.value(), body);
  }

  private PointChargeResult failure(ErrorCode errorCode) {
    JsonNode body =
        objectMapper.valueToTree(ApiResponse.failure(errorCode.name(), errorCode.message()));
    return new PointChargeResult(errorCode.httpStatus().value(), body);
  }

  private void complete(
      IdempotencyClaim claim, PointChargeResult result, String resultCode, Instant now) {
    idempotencyService.complete(
        claim.recordId(), result.httpStatus(), resultCode, result.body(), now);
  }
}
