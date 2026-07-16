package com.example.coffeeordersystem.menu;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

record PopularMenuWindow(Instant from, Instant to) {

  static PopularMenuWindow endingAt(Instant to) {
    Instant normalizedTo = ceilToMicrosecond(to);
    return new PopularMenuWindow(normalizedTo.minus(7, ChronoUnit.DAYS), normalizedTo);
  }

  private static Instant ceilToMicrosecond(Instant instant) {
    Instant truncated = instant.truncatedTo(ChronoUnit.MICROS);
    return truncated.equals(instant) ? instant : truncated.plus(1, ChronoUnit.MICROS);
  }
}
