package com.example.coffeeordersystem.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutboxRetryPolicyTest {

  private final OutboxRetryPolicy retryPolicy = new OutboxRetryPolicy();
  private final Instant failedAt = Instant.parse("2026-07-16T00:00:00Z");

  @Test
  @DisplayName("UT-OUTBOX-001 실패 횟수별 1분·5분·30분 재시도를 예약한다")
  void schedulesApprovedRetryDelays() {
    assertEquals(failedAt.plusSeconds(60), retryPolicy.nextRetryAt(1, failedAt).orElseThrow());
    assertEquals(failedAt.plusSeconds(300), retryPolicy.nextRetryAt(2, failedAt).orElseThrow());
    assertEquals(failedAt.plusSeconds(1_800), retryPolicy.nextRetryAt(3, failedAt).orElseThrow());
    assertTrue(retryPolicy.nextRetryAt(4, failedAt).isEmpty());
  }
}
