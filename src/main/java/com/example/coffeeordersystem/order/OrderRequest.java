package com.example.coffeeordersystem.order;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

record OrderRequest(@NotNull @Positive Long userId, @NotNull @Positive Long menuId) {}
