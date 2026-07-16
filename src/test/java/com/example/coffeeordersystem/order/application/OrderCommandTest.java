package com.example.coffeeordersystem.order.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.coffeeordersystem.common.error.ApiException;
import com.example.coffeeordersystem.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderCommandTest {

  private static final String UUID = "11111111-1111-4111-8111-111111111111";

  @Test
  @DisplayName("UT-ORDER-001 양의 사용자·메뉴와 정규화된 멱등키로 주문 명령을 만든다")
  void createsValidatedOrderCommand() {
    OrderCommand command = OrderCommand.from(1L, 2L, "AAAAAAAA-AAAA-4AAA-8AAA-AAAAAAAAAAAA");

    assertEquals(1L, command.userId());
    assertEquals(2L, command.menuId());
    assertEquals("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", command.idempotencyKey());
  }

  @Test
  @DisplayName("UT-ORDER-001 유효하지 않은 사용자·메뉴·멱등키 주문 명령을 거절한다")
  void rejectsInvalidOrderCommand() {
    assertError(() -> OrderCommand.from(null, 2L, UUID));
    assertError(() -> OrderCommand.from(0L, 2L, UUID));
    assertError(() -> OrderCommand.from(1L, null, UUID));
    assertError(() -> OrderCommand.from(1L, 0L, UUID));
    assertError(() -> OrderCommand.from(1L, 2L, "invalid"));
  }

  private void assertError(Runnable action) {
    ApiException exception = assertThrows(ApiException.class, action::run);
    assertEquals(ErrorCode.INVALID_REQUEST, exception.errorCode());
  }
}
