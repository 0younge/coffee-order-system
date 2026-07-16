package com.example.coffeeordersystem.menu;

public record MenuResponse(long menuId, String name, long price) {

  static MenuResponse from(Menu menu) {
    return new MenuResponse(menu.id(), menu.name(), menu.price());
  }
}
