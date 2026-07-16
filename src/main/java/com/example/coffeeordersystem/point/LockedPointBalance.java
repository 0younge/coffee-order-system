package com.example.coffeeordersystem.point;

import java.time.Instant;

public final class LockedPointBalance {

  private final PointAccount account;

  LockedPointBalance(PointAccount account) {
    this.account = account;
  }

  public boolean pay(long amount, Instant now) {
    return account.pay(amount, now);
  }

  public long balance() {
    return account.pointBalance();
  }
}
