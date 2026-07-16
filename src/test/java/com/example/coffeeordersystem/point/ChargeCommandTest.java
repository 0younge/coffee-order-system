package com.example.coffeeordersystem.point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.coffeeordersystem.common.error.ApiException;
import com.example.coffeeordersystem.common.error.ErrorCode;
import java.math.BigInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChargeCommandTest {

  private static final String UUID = "11111111-1111-4111-8111-111111111111";

  @Test
  @DisplayName("UT-POINT-001 signed BIGINT 범위의 양수 충전 명령을 만든다")
  void createsValidCommand() {
    ChargeCommand command =
        ChargeCommand.from(new ChargeRequest(7L, BigInteger.valueOf(Long.MAX_VALUE)), UUID);

    assertEquals(7L, command.userId());
    assertEquals(Long.MAX_VALUE, command.amount());
    assertEquals(UUID, command.idempotencyKey());
  }

  @Test
  @DisplayName("UT-POINT-002 0·음수·signed BIGINT 범위 밖 금액을 거절한다")
  void rejectsInvalidAmounts() {
    assertError(
        ErrorCode.INVALID_CHARGE_AMOUNT,
        () -> ChargeCommand.from(new ChargeRequest(7L, BigInteger.ZERO), UUID));
    assertError(
        ErrorCode.INVALID_CHARGE_AMOUNT,
        () -> ChargeCommand.from(new ChargeRequest(7L, BigInteger.valueOf(-1)), UUID));
    assertError(
        ErrorCode.INVALID_CHARGE_AMOUNT,
        () ->
            ChargeCommand.from(
                new ChargeRequest(7L, BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)),
                UUID));
  }

  @Test
  @DisplayName("UT-POINT-002 UUID는 대문자를 허용해 표준 소문자로 저장하고 축약형은 거절한다")
  void normalizesCanonicalUuid() {
    ChargeCommand command =
        ChargeCommand.from(
            new ChargeRequest(7L, BigInteger.ONE), "AAAAAAAA-AAAA-4AAA-8AAA-AAAAAAAAAAAA");

    assertEquals("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", command.idempotencyKey());
    assertError(
        ErrorCode.INVALID_REQUEST,
        () -> ChargeCommand.from(new ChargeRequest(7L, BigInteger.ONE), "1-1-1-1-1"));
  }

  private void assertError(ErrorCode errorCode, Runnable action) {
    ApiException exception = assertThrows(ApiException.class, action::run);
    assertEquals(errorCode, exception.errorCode());
  }
}
