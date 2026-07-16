package com.example.coffeeordersystem.point.application;

import com.example.coffeeordersystem.common.error.ApiException;
import com.example.coffeeordersystem.common.error.ErrorCode;
import com.example.coffeeordersystem.idempotency.application.IdempotencyKeyNormalizer;
import java.math.BigInteger;

public record ChargeCommand(long userId, long amount, String idempotencyKey) {

  private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);

  public static ChargeCommand from(
      Long userId, BigInteger requestedAmount, String rawIdempotencyKey) {
    if (userId == null || userId <= 0) {
      throw new ApiException(ErrorCode.INVALID_REQUEST);
    }
    long amount = validAmount(requestedAmount);
    String idempotencyKey = IdempotencyKeyNormalizer.normalize(rawIdempotencyKey);
    return new ChargeCommand(userId, amount, idempotencyKey);
  }

  private static long validAmount(BigInteger amount) {
    if (amount == null || amount.signum() <= 0 || amount.compareTo(MAX_LONG) > 0) {
      throw new ApiException(ErrorCode.INVALID_CHARGE_AMOUNT);
    }
    return amount.longValueExact();
  }
}
