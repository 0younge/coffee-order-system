package com.example.coffeeordersystem.menu.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "menus")
public class Menu {

  @Id private Long id;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false)
  private long price;

  public Long id() {
    return id;
  }

  public String name() {
    return name;
  }

  public long price() {
    return price;
  }
}
