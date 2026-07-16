package com.example.coffeeordersystem.menu.api;

import com.example.coffeeordersystem.menu.application.PopularMenuResult;

public record PopularMenuResponse(int rank, long menuId, String name, long price, long orderCount) {

  static PopularMenuResponse from(PopularMenuResult result) {
    return new PopularMenuResponse(
        result.rank(), result.menuId(), result.name(), result.price(), result.orderCount());
  }
}
