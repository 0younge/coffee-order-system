package com.example.coffeeordersystem.order.application;

import com.example.coffeeordersystem.common.error.ApiException;
import com.example.coffeeordersystem.common.error.ErrorCode;
import com.example.coffeeordersystem.idempotency.application.IdempotencyKeyNormalizer;

public final class OrderCommand {

  private final long userId;
  private final long menuId;
  private final String idempotencyKey;

  private OrderCommand(long userId, long menuId, String idempotencyKey) {
    this.userId = userId;
    this.menuId = menuId;
    this.idempotencyKey = idempotencyKey;
  }

  public static OrderCommand from(Long userId, Long menuId, String rawIdempotencyKey) {
    if (userId == null || userId <= 0 || menuId == null || menuId <= 0) {
      throw new ApiException(ErrorCode.INVALID_REQUEST);
    }
    return new OrderCommand(userId, menuId, IdempotencyKeyNormalizer.normalize(rawIdempotencyKey));
  }

  public long userId() {
    return userId;
  }

  public long menuId() {
    return menuId;
  }

  public String idempotencyKey() {
    return idempotencyKey;
  }
}
