package com.example.coffeeordersystem.order.application;

import java.time.Instant;

record OrderData(
    long orderId,
    long menuId,
    String menuName,
    long paidAmount,
    long remainingBalance,
    Instant paidAt) {}
