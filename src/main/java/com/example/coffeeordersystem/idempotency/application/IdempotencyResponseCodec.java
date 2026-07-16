package com.example.coffeeordersystem.idempotency.application;

public interface IdempotencyResponseCodec {

  String encodeSuccess(String code, String message, Object data);

  String encodeFailure(String code, String message);
}
