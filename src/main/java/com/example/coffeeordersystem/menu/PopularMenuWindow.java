package com.example.coffeeordersystem.menu;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

record PopularMenuWindow(Instant from, Instant to) {

  static PopularMenuWindow endingAt(Instant to) {
    return new PopularMenuWindow(to.minus(7, ChronoUnit.DAYS), to);
  }
}
