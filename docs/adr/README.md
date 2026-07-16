# 아키텍처 결정 기록

이 디렉터리는 구현자가 “무엇을 선택했는가”뿐 아니라 “왜 선택했는가”를 확인하는 정본이다. 파일 하나는 되돌리기 비싼 결정 하나만 다룬다.

## 상태 의미

| 상태 | 의미 |
|---|---|
| 제안됨 | 결정 방향을 문서화했으며 최종 승인 전이다. 구현 전에 프로젝트 소유자의 승인을 받는다. |
| 승인됨 | 구현 기준으로 확정됐다. 본문을 직접 고치지 않고 변경 시 새 ADR로 대체한다. |
| 대체됨 | 더 새로운 ADR이 이 결정을 대신한다. |

## 결정 목록

| 번호 | 결정 | 상태 |
|---:|---|---|
| [0001](./0001-use-database-pessimistic-locking-for-points.md) | 포인트 변경에 DB 비관적 락 사용 | 승인됨 |
| [0002](./0002-protect-mutations-with-idempotency-keys.md) | 충전과 주문에 멱등키 적용 | 대체됨 (→ 0015) |
| [0003](./0003-deliver-order-events-with-transactional-outbox.md) | 주문 이벤트를 Transactional Outbox로 전달 | 승인됨 |
| [0004](./0004-calculate-popular-menus-from-paid-orders.md) | 인기 메뉴를 결제 완료 주문에서 직접 집계 | 승인됨 |
| [0005](./0005-establish-java-spring-mysql-platform-baseline.md) | Java·Spring·MySQL 플랫폼 기준선 확정 | 승인됨 |
| [0006](./0006-use-feature-oriented-modular-monolith.md) | 기능 중심 모듈러 모놀리스 사용 | 승인됨 |
| [0007](./0007-store-current-point-balance-without-ledger.md) | 포인트 원장 없이 현재 잔액 저장 | 대체됨 (→ 0016) |
| [0008](./0008-use-stateless-jwt-access-token-authentication.md) | JWT Access Token 기반 무상태 인증 사용 | 대체됨 (→ 0022) |
| [0009](./0009-model-single-menu-orders-with-snapshots.md) | 단일 메뉴 주문과 주문 시점 스냅샷 사용 | 승인됨 |
| [0010](./0010-test-against-mysql-with-docker-compose.md) | Docker Compose의 실제 MySQL로 통합 테스트 | 승인됨 |
| [0011](./0011-return-temporary-unavailable-on-database-contention.md) | DB 경합 timeout과 deadlock에 일시적 오류 반환 | 승인됨 |
| [0012](./0012-use-spring-security-for-jwt.md) | JWT 구현에 Spring Security 사용 | 대체됨 (→ 0022) |
| [0013](./0013-use-actuator-and-micrometer-for-observability.md) | 관측성에 Actuator와 Micrometer 사용 | 승인됨 |
| [0014](./0014-send-outbox-batches-asynchronously.md) | Outbox 배치를 비동기로 병렬 전송 | 승인됨 |
| [0015](./0015-protect-mutations-with-idempotency-keys.md) | 충전과 주문에 멱등키 적용 | 승인됨 |
| [0016](./0016-store-positive-point-balance-without-arbitrary-cap.md) | 임의 상한 없이 현재 포인트 잔액 저장 | 승인됨 |
| [0017](./0017-bound-first-outbox-attempt-latency.md) | Outbox 최초 전송 시도 시간을 제한 | 승인됨 |
| [0018](./0018-isolate-test-database-and-outbox-workers.md) | 테스트 데이터베이스와 Outbox 워커 격리 | 승인됨 |
| [0019](./0019-use-spotless-as-format-gate.md) | Spotless를 코드 포맷 완료 게이트로 사용 | 승인됨 |
| [0020](./0020-enforce-lifecycle-invariants-with-database-checks.md) | 상태 수명주기 불변식을 DB CHECK로 강제 | 승인됨 |
| [0021](./0021-configure-stateless-bearer-security-boundary.md) | 무상태 Bearer REST 보안 경계 구성 | 대체됨 (→ 0022) |
| [0022](./0022-accept-user-id-without-authentication.md) | 인증 없이 요청 본문의 사용자 ID 사용 | 승인됨 |
| [0023](./0023-define-outbox-field-lifecycle.md) | Outbox 상태별 필드 수명주기 확정 | 승인됨 |
| [0024](./0024-seed-reference-user-for-local-execution.md) | 과제와 로컬 실행용 기준 사용자 seed | 승인됨 |
| [0025](./0025-lock-user-before-idempotency-record.md) | 사용자 행을 멱등 레코드보다 먼저 잠금 | 승인됨 |

ADR 0022는 인증 결정을 직접 다룬 0008·0012·0021을 완전히 대체한다. 또한 0005의 Spring Security 기준선, 0006의 Auth 모듈, 0015의 인증 실패 예외, 0018의 공유 JWT 키 조건을 부분 대체한다. 이 조항들은 구현 계약으로 사용하지 않으며 각 ADR의 나머지 결정만 유지한다.

ADR 0024는 ADR 0022의 기존 사용자 준비 절차 제외 조항만 부분 대체한다. 사용자 생성·관리 API와 인증·인가 제외, 요청 본문의 기존 `userId` 사용은 그대로 유지한다.

## 변경 규칙

1. 제안 상태에서는 검토 의견을 반영해 같은 파일을 다듬는다.
2. 프로젝트 소유자가 확정하면 상태를 `승인됨`으로 변경한다.
3. 승인된 결정은 본문을 수정하지 않는다. 다른 선택으로 바꿀 때 다음 번호 ADR을 만들고 기존 상태를 `대체됨 (→ NNNN)`으로 변경한다.
