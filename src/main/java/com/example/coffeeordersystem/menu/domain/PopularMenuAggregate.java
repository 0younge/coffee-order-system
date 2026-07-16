package com.example.coffeeordersystem.menu.domain;

public record PopularMenuAggregate(long menuId, String name, long price, long orderCount) {}
