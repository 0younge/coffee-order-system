package com.example.coffeeordersystem.menu.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public final class PopularMenuWindow {

  private final Instant from;
  private final Instant to;

  private PopularMenuWindow(Instant from, Instant to) {
    this.from = from;
    this.to = to;
  }

  public static PopularMenuWindow endingAt(Instant to) {
    Instant normalizedTo = ceilToMicrosecond(to);
    return new PopularMenuWindow(normalizedTo.minus(7, ChronoUnit.DAYS), normalizedTo);
  }

  private static Instant ceilToMicrosecond(Instant instant) {
    Instant truncated = instant.truncatedTo(ChronoUnit.MICROS);
    return truncated.equals(instant) ? instant : truncated.plus(1, ChronoUnit.MICROS);
  }

  public Instant from() {
    return from;
  }

  public Instant to() {
    return to;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof PopularMenuWindow window)) {
      return false;
    }
    return from.equals(window.from) && to.equals(window.to);
  }

  @Override
  public int hashCode() {
    return Objects.hash(from, to);
  }

  @Override
  public String toString() {
    return "PopularMenuWindow[from=" + from + ", to=" + to + "]";
  }
}
