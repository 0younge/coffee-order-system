package com.example.coffeeordersystem.point;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "users")
class PointAccount {

  @Id private Long id;

  @Column(name = "point_balance", nullable = false)
  private long pointBalance;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected PointAccount() {}

  PointAccount(long id, long pointBalance, Instant updatedAt) {
    this.id = id;
    this.pointBalance = pointBalance;
    this.updatedAt = updatedAt;
  }

  long pointBalance() {
    return pointBalance;
  }

  void charge(long amount, Instant now) {
    pointBalance = Math.addExact(pointBalance, amount);
    updatedAt = now;
  }

  boolean pay(long amount, Instant now) {
    if (pointBalance < amount) {
      return false;
    }
    pointBalance -= amount;
    updatedAt = now;
    return true;
  }
}
