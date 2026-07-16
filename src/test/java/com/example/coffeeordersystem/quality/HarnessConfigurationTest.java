package com.example.coffeeordersystem.quality;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HarnessConfigurationTest {

  @Test
  @DisplayName("QT-HARNESS-001 하네스와 트랜잭션 문서가 현재 구현을 안내한다")
  void keepsHarnessAndTransactionGuidanceCurrent() throws IOException {
    String agents = Files.readString(Path.of("AGENTS.md"));
    String claude = Files.readString(Path.of("CLAUDE.md"));
    String context = Files.readString(Path.of("docs/context.md"));
    String architecture = Files.readString(Path.of("docs/architecture.md"));

    assertTrue(agents.lines().count() <= 100, "AGENTS.md는 탐색성을 위해 100줄 이하여야 합니다.");
    assertTrue(claude.contains("@AGENTS.md"), "CLAUDE.md는 AGENTS.md 포인터를 유지해야 합니다.");
    assertFalse(agents.contains("Flyway·업무 기능은 아직 구현 전"));
    assertTrue(agents.contains("메뉴·포인트·주문·인기 메뉴 API와 Outbox 워커"));
    assertTrue(agents.contains("fresh MySQL CI"));
    assertTrue(agents.contains("예약된 정리 기능"));
    assertTrue(agents.contains("`PT-*`는 제외"));
    assertFalse(architecture.contains("Flyway 파일 구성은 구현 시 정"));
    assertFalse(architecture.contains("구현 시 코드와 함께 확정"));
    assertLockBeforeIdempotency(section(context, "### 포인트 충전", "### 주문 및 결제"));
    assertLockBeforeIdempotency(section(context, "### 주문 및 결제", "### 외부 이벤트 전달"));
    assertTrue(
        architecture.contains(
            "[ADR-0025: 외래 키 교착을 피하도록 사용자 행을 멱등 레코드보다 먼저 잠금]"
                + "(./adr/0025-lock-user-before-idempotency-record.md)"));
  }

  private String section(String document, String startHeading, String endHeading) {
    int start = document.indexOf(startHeading);
    int end = document.indexOf(endHeading, start + startHeading.length());
    assertTrue(start >= 0 && end > start, startHeading + " 문서 구간이 있어야 합니다.");
    return document.substring(start, end);
  }

  private void assertLockBeforeIdempotency(String section) {
    int lock = section.indexOf("1. 사용자 행을 비관적으로 잠");
    int idempotency = section.indexOf("\n2. ", lock);
    int idempotencyLineEnd = section.indexOf('\n', idempotency + 1);
    assertTrue(lock >= 0, "사용자 행 잠금 단계가 있어야 합니다.");
    assertTrue(idempotency > lock, "사용자 행 잠금 뒤 두 번째 단계가 있어야 합니다.");
    assertTrue(
        section.substring(idempotency, idempotencyLineEnd).contains("멱등"),
        "멱등 선점은 사용자 행 잠금 다음 단계여야 합니다.");
  }
}
