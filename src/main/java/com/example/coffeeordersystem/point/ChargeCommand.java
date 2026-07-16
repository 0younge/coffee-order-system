package com.example.coffeeordersystem.point;

import com.example.coffeeordersystem.common.error.ApiException;
import com.example.coffeeordersystem.common.error.ErrorCode;
import com.example.coffeeordersystem.idempotency.application.IdempotencyKeyNormalizer;
import java.math.BigInteger;

record ChargeCommand(long userId, long amount, String idempotencyKey) {

  private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);

  static ChargeCommand from(ChargeRequest request, String rawIdempotencyKey) {
    if (request == null || request.userId() == null || request.userId() <= 0) {
      throw new ApiException(ErrorCode.INVALID_REQUEST);
    }
    long amount = validAmount(request.amount());
    String idempotencyKey = IdempotencyKeyNormalizer.normalize(rawIdempotencyKey);
    return new ChargeCommand(request.userId(), amount, idempotencyKey);
  }

  private static long validAmount(BigInteger amount) {
    if (amount == null || amount.signum() <= 0 || amount.compareTo(MAX_LONG) > 0) {
      throw new ApiException(ErrorCode.INVALID_CHARGE_AMOUNT);
    }
    return amount.longValueExact();
  }
}
