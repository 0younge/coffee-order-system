package com.example.coffeeordersystem.menu.application;

import com.example.coffeeordersystem.menu.domain.Menu;

public record MenuSnapshot(long menuId, String name, long price) {

  static MenuSnapshot from(Menu menu) {
    return new MenuSnapshot(menu.id(), menu.name(), menu.price());
  }
}
