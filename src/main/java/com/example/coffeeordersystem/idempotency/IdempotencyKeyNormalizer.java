package com.example.coffeeordersystem.idempotency;

import com.example.coffeeordersystem.common.error.ApiException;
import com.example.coffeeordersystem.common.error.ErrorCode;
import java.util.Locale;
import java.util.UUID;

public final class IdempotencyKeyNormalizer {

  private IdempotencyKeyNormalizer() {}

  public static String normalize(String rawIdempotencyKey) {
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
