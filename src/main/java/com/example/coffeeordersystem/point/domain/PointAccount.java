package com.example.coffeeordersystem.point.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "users")
public class PointAccount {

  @Id private Long id;

  @Column(name = "point_balance", nullable = false)
  private long pointBalance;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  PointAccount(long id, long pointBalance, Instant updatedAt) {
    this.id = id;
    this.pointBalance = pointBalance;
    this.updatedAt = updatedAt;
  }

  public long pointBalance() {
    return pointBalance;
  }

  public void charge(long amount, Instant now) {
    pointBalance = Math.addExact(pointBalance, amount);
    updatedAt = now;
  }

  public boolean pay(long amount, Instant now) {
    if (pointBalance < amount) {
      return false;
    }
    pointBalance -= amount;
    updatedAt = now;
    return true;
  }
}
