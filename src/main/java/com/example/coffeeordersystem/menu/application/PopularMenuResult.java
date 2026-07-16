package com.example.coffeeordersystem.menu.application;

public record PopularMenuResult(int rank, long menuId, String name, long price, long orderCount) {}
