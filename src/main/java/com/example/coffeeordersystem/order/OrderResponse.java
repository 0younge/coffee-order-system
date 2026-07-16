package com.example.coffeeordersystem.order;

import java.time.Instant;

record OrderResponse(
    long orderId,
    long menuId,
    String menuName,
    long paidAmount,
    long remainingBalance,
    Instant paidAt) {}
