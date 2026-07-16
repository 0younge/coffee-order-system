package com.example.coffeeordersystem.idempotency.infrastructure;

import com.example.coffeeordersystem.common.api.ApiResponse;
import com.example.coffeeordersystem.common.api.ApiResponseJsonCodec;
import com.example.coffeeordersystem.idempotency.application.IdempotencyResponseCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
class IdempotencyJsonResponseCodec implements IdempotencyResponseCodec {

  private final ApiResponseJsonCodec responseJsonCodec;

  @Override
  public String encodeSuccess(String code, String message, Object data) {
    return responseJsonCodec.write(ApiResponse.success(code, message, data));
  }

  @Override
  public String encodeFailure(String code, String message) {
    return responseJsonCodec.write(ApiResponse.failure(code, message));
  }
}
