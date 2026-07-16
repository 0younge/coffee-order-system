package com.example.coffeeordersystem.point.application;

import com.example.coffeeordersystem.common.error.ApiException;
import com.example.coffeeordersystem.common.error.ErrorCode;
import com.example.coffeeordersystem.idempotency.application.IdempotencyKeyNormalizer;
import java.math.BigInteger;

public final class ChargeCommand {

  private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);
  private final long userId;
  private final long amount;
  private final String idempotencyKey;

  private ChargeCommand(long userId, long amount, String idempotencyKey) {
    this.userId = userId;
    this.amount = amount;
    this.idempotencyKey = idempotencyKey;
  }

  public static ChargeCommand from(
      Long userId, BigInteger requestedAmount, String rawIdempotencyKey) {
    if (userId == null || userId <= 0) {
      throw new ApiException(ErrorCode.INVALID_REQUEST);
    }
    long amount = validAmount(requestedAmount);
    String idempotencyKey = IdempotencyKeyNormalizer.normalize(rawIdempotencyKey);
    return new ChargeCommand(userId, amount, idempotencyKey);
  }

  public long userId() {
    return userId;
  }

  public long amount() {
    return amount;
  }

  public String idempotencyKey() {
    return idempotencyKey;
  }

  private static long validAmount(BigInteger amount) {
    if (amount == null || amount.signum() <= 0 || amount.compareTo(MAX_LONG) > 0) {
      throw new ApiException(ErrorCode.INVALID_CHARGE_AMOUNT);
    }
    return amount.longValueExact();
  }
}
