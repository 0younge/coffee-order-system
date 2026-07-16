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
    requirePositive(amount);
    pointBalance = Math.addExact(pointBalance, amount);
    updatedAt = now;
  }

  public boolean pay(long amount, Instant now) {
    requirePositive(amount);
    if (pointBalance < amount) {
      return false;
    }
    pointBalance -= amount;
    updatedAt = now;
    return true;
  }

  private void requirePositive(long amount) {
    if (amount <= 0) {
      throw new IllegalArgumentException("포인트 변경 금액은 양수여야 합니다.");
    }
  }
}
