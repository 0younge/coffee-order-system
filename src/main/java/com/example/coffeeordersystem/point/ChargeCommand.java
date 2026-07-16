package com.example.coffeeordersystem.point;

import com.example.coffeeordersystem.common.error.ApiException;
import com.example.coffeeordersystem.common.error.ErrorCode;
import java.math.BigInteger;
import java.util.Locale;
import java.util.UUID;

record ChargeCommand(long userId, long amount, String idempotencyKey) {

  private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);

  static ChargeCommand from(ChargeRequest request, String rawIdempotencyKey) {
    if (request == null || request.userId() == null || request.userId() <= 0) {
      throw new ApiException(ErrorCode.INVALID_REQUEST);
    }
    long amount = validAmount(request.amount());
    String idempotencyKey = normalizeUuid(rawIdempotencyKey);
    return new ChargeCommand(request.userId(), amount, idempotencyKey);
  }

  private static long validAmount(BigInteger amount) {
    if (amount == null || amount.signum() <= 0 || amount.compareTo(MAX_LONG) > 0) {
      throw new ApiException(ErrorCode.INVALID_CHARGE_AMOUNT);
    }
    return amount.longValueExact();
  }

  private static String normalizeUuid(String rawIdempotencyKey) {
    if (rawIdempotencyKey == null) {
      throw new ApiException(ErrorCode.INVALID_REQUEST);
    }
    try {
      UUID uuid = UUID.fromString(rawIdempotencyKey);
      String normalized = uuid.toString();
      if (!normalized.equals(rawIdempotencyKey.toLowerCase(Locale.ROOT))) {
        throw new ApiException(ErrorCode.INVALID_REQUEST);
      }
      return normalized;
    } catch (IllegalArgumentException exception) {
      throw new ApiException(ErrorCode.INVALID_REQUEST);
    }
  }
}
