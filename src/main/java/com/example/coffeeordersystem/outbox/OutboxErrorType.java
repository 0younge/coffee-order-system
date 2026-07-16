package com.example.coffeeordersystem.outbox;

enum OutboxErrorType {
  TIMEOUT,
  NETWORK,
  HTTP_3XX,
  HTTP_4XX,
  HTTP_5XX
}
