package com.example.coffeeordersystem.menu;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "menus")
public class Menu {

  @Id private Long id;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false)
  private long price;

  protected Menu() {}

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
