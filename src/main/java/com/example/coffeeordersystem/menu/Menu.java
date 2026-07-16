package com.example.coffeeordersystem.menu;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "menus")
class Menu {

  @Id private Long id;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false)
  private long price;

  Long id() {
    return id;
  }

  String name() {
    return name;
  }

  long price() {
    return price;
  }
}
