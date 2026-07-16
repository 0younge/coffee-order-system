package com.example.coffeeordersystem.order.domain;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderTest {

  private static final Instant PAID_AT = Instant.parse("2026-07-16T06:00:00.123456Z");

  @Test
  @DisplayName("UT-ORDER-002 결제 완료 주문을 이름 있는 팩터리로 만든다")
  void createsPaidOrder() {
    assertNotNull(Order.paid(1L, 2L, "메뉴", 4_000L, PAID_AT));
  }

  @Test
  @DisplayName("UT-ORDER-002 결제 완료 주문의 식별자·스냅샷·금액·시각 불변식을 강제한다")
  void rejectsInvalidPaidOrder() {
    assertInvalid(() -> Order.paid(0L, 2L, "메뉴", 4_000L, PAID_AT));
    assertInvalid(() -> Order.paid(1L, 0L, "메뉴", 4_000L, PAID_AT));
    assertInvalid(() -> Order.paid(1L, 2L, null, 4_000L, PAID_AT));
    assertInvalid(() -> Order.paid(1L, 2L, " ", 4_000L, PAID_AT));
    assertInvalid(() -> Order.paid(1L, 2L, "가".repeat(101), 4_000L, PAID_AT));
    assertInvalid(() -> Order.paid(1L, 2L, "메뉴", 0L, PAID_AT));
    assertInvalid(() -> Order.paid(1L, 2L, "메뉴", 4_000L, null));
  }

  private void assertInvalid(Runnable action) {
    assertThrows(IllegalArgumentException.class, action::run);
  }
}
