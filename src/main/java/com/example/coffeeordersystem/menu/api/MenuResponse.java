package com.example.coffeeordersystem.menu.api;

import com.example.coffeeordersystem.menu.application.MenuItemResult;

public record MenuResponse(long menuId, String name, long price) {

  static MenuResponse from(MenuItemResult result) {
    return new MenuResponse(result.menuId(), result.name(), result.price());
  }
}
