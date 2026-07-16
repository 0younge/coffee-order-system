package com.example.coffeeordersystem.menu;

import java.util.List;
import java.util.stream.IntStream;

final class PopularMenuRanking {

  private static final int MAX_RESULTS = 3;

  private PopularMenuRanking() {}

  static List<PopularMenuResponse> rank(List<PopularMenuAggregate> sortedAggregates) {
    int resultCount = Math.min(sortedAggregates.size(), MAX_RESULTS);
    return IntStream.range(0, resultCount)
        .mapToObj(
            index -> {
              PopularMenuAggregate aggregate = sortedAggregates.get(index);
              return new PopularMenuResponse(
                  index + 1,
                  aggregate.menuId(),
                  aggregate.name(),
                  aggregate.price(),
                  aggregate.orderCount());
            })
        .toList();
  }
}
