package com.example.coffeeordersystem.idempotency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.example.coffeeordersystem.idempotency.application.IdempotencyOperation;
import com.example.coffeeordersystem.idempotency.application.RequestHasher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequestHasherTest {

  private final RequestHasher requestHasher = new RequestHasher();

  @Test
  @DisplayName("UT-IDEM-001 정규화한 요청의 UTF-8 SHA-256을 결정적으로 계산한다")
  void hashesCanonicalRequest() {
    assertEquals(
        "f9ab25dc2e650f9b464937205ce2bd1bfe0184061169aa8aad3607c5ff56adaa",
        requestHasher.hash(IdempotencyOperation.CHARGE, 10000));
    assertEquals(
        "9ac83f15c55de7dd91a143b96f0bc082b40c6cbdd4f7ba6d099aff0150584805",
        requestHasher.hash(IdempotencyOperation.ORDER, 3));
  }

  @Test
  @DisplayName("UT-IDEM-002 작업 유형이나 업무 값이 다르면 다른 해시를 만든다")
  void distinguishesDifferentRequests() {
    String charge = requestHasher.hash(IdempotencyOperation.CHARGE, 1);

    assertNotEquals(charge, requestHasher.hash(IdempotencyOperation.CHARGE, 2));
    assertNotEquals(charge, requestHasher.hash(IdempotencyOperation.ORDER, 1));
  }
}
