package com.example.coffeeordersystem.point.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigInteger;

record ChargeRequest(@NotNull @Positive Long userId, @NotNull BigInteger amount) {}
