package com.example.coffeeordersystem.outbox.domain;

public enum OutboxErrorType {
  TIMEOUT,
  NETWORK,
  HTTP_3XX,
  HTTP_4XX,
  HTTP_5XX
}
