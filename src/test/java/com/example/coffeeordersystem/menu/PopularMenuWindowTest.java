package com.example.coffeeordersystem.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
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

  @Test
  @DisplayName("UT-POPULAR-002 정렬된 집계에 1부터 순위를 붙이고 최대 3개만 반환한다")
  void ranksAtMostThreeSortedAggregates() {
    List<PopularMenuResponse> responses =
        PopularMenuRanking.rank(
            List.of(aggregate(1L, 4L), aggregate(2L, 4L), aggregate(3L, 2L), aggregate(4L, 1L)));

    assertEquals(List.of(1, 2, 3), responses.stream().map(PopularMenuResponse::rank).toList());
    assertEquals(List.of(1L, 2L, 3L), responses.stream().map(PopularMenuResponse::menuId).toList());
  }

  private PopularMenuAggregate aggregate(long menuId, long orderCount) {
    return new PopularMenuAggregate(menuId, "메뉴 " + menuId, 1_000L, orderCount);
  }
}
