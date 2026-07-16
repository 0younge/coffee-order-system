package com.example.coffeeordersystem.menu.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public record PopularMenuWindow(Instant from, Instant to) {

  public static PopularMenuWindow endingAt(Instant to) {
    Instant normalizedTo = ceilToMicrosecond(to);
    return new PopularMenuWindow(normalizedTo.minus(7, ChronoUnit.DAYS), normalizedTo);
  }

  private static Instant ceilToMicrosecond(Instant instant) {
    Instant truncated = instant.truncatedTo(ChronoUnit.MICROS);
    return truncated.equals(instant) ? instant : truncated.plus(1, ChronoUnit.MICROS);
  }
}
