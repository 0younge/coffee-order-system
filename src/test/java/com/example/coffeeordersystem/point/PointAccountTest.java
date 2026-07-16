package com.example.coffeeordersystem.point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
