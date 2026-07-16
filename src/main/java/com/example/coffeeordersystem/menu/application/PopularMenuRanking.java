package com.example.coffeeordersystem.menu.application;

import com.example.coffeeordersystem.menu.domain.PopularMenuAggregate;
import java.util.List;
import java.util.stream.IntStream;

final class PopularMenuRanking {

  private static final int MAX_RESULTS = 3;

  private PopularMenuRanking() {}

  static List<PopularMenuResult> rank(List<PopularMenuAggregate> sortedAggregates) {
    int resultCount = Math.min(sortedAggregates.size(), MAX_RESULTS);
    return IntStream.range(0, resultCount)
        .mapToObj(
            index -> {
              PopularMenuAggregate aggregate = sortedAggregates.get(index);
              return new PopularMenuResult(
                  index + 1,
                  aggregate.menuId(),
                  aggregate.name(),
                  aggregate.price(),
                  aggregate.orderCount());
            })
        .toList();
  }
}
