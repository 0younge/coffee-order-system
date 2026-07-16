package com.example.coffeeordersystem.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "orders")
class Order {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private long userId;

  @Column(name = "menu_id", nullable = false)
  private long menuId;

  @Column(name = "menu_name_snapshot", nullable = false, length = 100)
  private String menuNameSnapshot;

  @Column(name = "paid_amount", nullable = false)
  private long paidAmount;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(name = "paid_at", nullable = false)
  private Instant paidAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected Order() {}

  private Order(
      long userId, long menuId, String menuNameSnapshot, long paidAmount, Instant paidAt) {
    this.userId = userId;
    this.menuId = menuId;
    this.menuNameSnapshot = menuNameSnapshot;
    this.paidAmount = paidAmount;
    this.status = "PAID";
    this.paidAt = paidAt;
    this.createdAt = paidAt;
  }

  static Order paid(
      long userId, long menuId, String menuNameSnapshot, long paidAmount, Instant paidAt) {
    return new Order(userId, menuId, menuNameSnapshot, paidAmount, paidAt);
  }

  long id() {
    return id;
  }
}
