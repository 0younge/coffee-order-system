package com.example.coffeeordersystem.outbox;

record OutboxClaim(String eventId, String claimToken, String payload) {}
