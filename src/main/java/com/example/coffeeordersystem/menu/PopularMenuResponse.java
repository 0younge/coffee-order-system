package com.example.coffeeordersystem.menu;

public record PopularMenuResponse(
    int rank, long menuId, String name, long price, long orderCount) {}
