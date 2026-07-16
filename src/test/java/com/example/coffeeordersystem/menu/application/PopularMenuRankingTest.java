package com.example.coffeeordersystem.menu.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.coffeeordersystem.menu.domain.PopularMenuAggregate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PopularMenuRankingTest {

  @Test
  @DisplayName("UT-POPULAR-002 정렬된 집계에 1부터 순위를 붙이고 최대 3개만 반환한다")
  void ranksAtMostThreeSortedAggregates() {
    List<PopularMenuResult> results =
        PopularMenuRanking.rank(
            List.of(aggregate(1L, 4L), aggregate(2L, 4L), aggregate(3L, 2L), aggregate(4L, 1L)));

    assertEquals(List.of(1, 2, 3), results.stream().map(PopularMenuResult::rank).toList());
    assertEquals(List.of(1L, 2L, 3L), results.stream().map(PopularMenuResult::menuId).toList());
  }

  private PopularMenuAggregate aggregate(long menuId, long orderCount) {
    return new PopularMenuAggregate(menuId, "메뉴 " + menuId, 1_000L, orderCount);
  }
}
