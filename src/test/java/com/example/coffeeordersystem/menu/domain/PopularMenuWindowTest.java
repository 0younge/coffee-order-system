package com.example.coffeeordersystem.menu.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PopularMenuWindowTest {

  @Test
  @DisplayName("UT-POPULAR-001 고정 시각에서 정확한 7일 반개구간을 계산한다")
  void calculatesSevenDayWindow() {
    Instant to = Instant.parse("2026-07-16T12:00:00Z");

    PopularMenuWindow window = PopularMenuWindow.endingAt(to);

    assertEquals(Instant.parse("2026-07-09T12:00:00Z"), window.from());
    assertEquals(to, window.to());
  }

  @Test
  @DisplayName("UT-POPULAR-001 DB 정밀도 밖의 조회 경계는 다음 microsecond로 올림한다")
  void ceilsNanosecondWindowToDatabasePrecision() {
    Instant to = Instant.parse("2026-07-16T12:00:00.123456499Z");

    PopularMenuWindow window = PopularMenuWindow.endingAt(to);

    assertEquals(Instant.parse("2026-07-09T12:00:00.123457Z"), window.from());
    assertEquals(Instant.parse("2026-07-16T12:00:00.123457Z"), window.to());
  }
}
