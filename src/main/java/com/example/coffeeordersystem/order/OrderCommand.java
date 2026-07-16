package com.example.coffeeordersystem.order;

import com.example.coffeeordersystem.common.error.ApiException;
import com.example.coffeeordersystem.common.error.ErrorCode;
import com.example.coffeeordersystem.idempotency.application.IdempotencyKeyNormalizer;

record OrderCommand(long userId, long menuId, String idempotencyKey) {

  static OrderCommand from(OrderRequest request, String rawIdempotencyKey) {
    if (request == null
        || request.userId() == null
        || request.userId() <= 0
        || request.menuId() == null
        || request.menuId() <= 0) {
      throw new ApiException(ErrorCode.INVALID_REQUEST);
    }
    return new OrderCommand(
        request.userId(), request.menuId(), IdempotencyKeyNormalizer.normalize(rawIdempotencyKey));
  }
}
