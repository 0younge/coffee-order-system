package com.example.coffeeordersystem.menu.application;

import com.example.coffeeordersystem.menu.domain.Menu;

public record MenuItemResult(long menuId, String name, long price) {

  static MenuItemResult from(Menu menu) {
    return new MenuItemResult(menu.id(), menu.name(), menu.price());
  }
}
