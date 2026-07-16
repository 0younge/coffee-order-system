package com.example.coffeeordersystem.point.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PointAccountTest {

  @Test
  @DisplayName("UT-POINT-001 충전액을 현재 잔액에 정확히 더한다")
  void chargesPointBalance() {
    Instant now = Instant.parse("2026-07-16T00:00:00Z");
    PointAccount account = new PointAccount(1L, 100L, now.minusSeconds(1));

    account.charge(250L, now);

    assertEquals(350L, account.pointBalance());
  }

  @Test
  @DisplayName("UT-POINT-002 잔액 덧셈 overflow를 상태 변경 없이 거절한다")
  void rejectsBalanceOverflow() {
    Instant now = Instant.parse("2026-07-16T00:00:00Z");
    PointAccount account = new PointAccount(1L, Long.MAX_VALUE, now.minusSeconds(1));

    assertThrows(ArithmeticException.class, () -> account.charge(1L, now));
    assertEquals(Long.MAX_VALUE, account.pointBalance());
  }

  @Test
  @DisplayName("UT-POINT-003 결제금액을 현재 잔액에서 정확히 차감한다")
  void paysFromCurrentBalance() {
    Instant now = Instant.parse("2026-07-16T00:00:00Z");
    PointAccount account = new PointAccount(1L, 5_000L, now.minusSeconds(1));

    boolean paid = account.pay(4_000L, now);

    assertTrue(paid);
    assertEquals(1_000L, account.pointBalance());
  }

  @Test
  @DisplayName("UT-POINT-004 잔액이 부족하면 포인트를 변경하지 않는다")
  void rejectsPaymentWhenBalanceIsInsufficient() {
    Instant now = Instant.parse("2026-07-16T00:00:00Z");
    PointAccount account = new PointAccount(1L, 3_999L, now.minusSeconds(1));

    boolean paid = account.pay(4_000L, now);

    assertFalse(paid);
    assertEquals(3_999L, account.pointBalance());
  }
}
