# 요구사항 추적성

## 1. 목적과 판정 기준

이 문서는 [PRD](./prd.md)의 기능·비기능 요구사항이 설계, API, ADR, 구현, 테스트에서 빠지지 않았는지 추적하는 작업대장이다. 설계 문서가 상세하더라도 실행 가능한 코드와 통과한 테스트가 없으면 구현 증거로 간주하지 않는다.

2026-07-15 현재 애플리케이션은 Spring Boot 부트스트랩 단계다. 아래 테스트 ID는 [테스트 전략](./test-strategy.md)에 배정한 **계획 식별자**이며, 테스트 소스와 통과 결과가 생기기 전까지 검증 증거가 아니다.

### 구현 상태

| 상태 | 의미 |
|---|---|
| `미구현` | 설계만 있거나 해당 소스가 없음 |
| `구현 중` | 일부 소스가 있으나 요구사항의 정상·실패 경로가 완성되지 않음 |
| `구현됨·미검증` | 소스는 완성됐지만 요구 테스트 또는 실행 증거가 없음 |
| `검증 실패` | 검증 명령을 실행했지만 하나 이상의 필수 검사나 테스트가 실패함 |
| `검증됨` | 구현, 대응 테스트, MySQL 기반 실행 결과가 모두 연결됨 |

상태는 가장 약한 근거를 따른다. 예를 들어 API 코드가 있어도 동시성 테스트가 없다면 동시성 요구사항은 `검증됨`이 아니다.

### 증거 규칙

검증 증거에는 다음 네 가지가 함께 있어야 한다.

1. 구현 소스 경로와 관련 메서드 또는 구성
2. 테스트 ID와 테스트 소스 경로
3. 실행 명령, 전체·성공·실패·제외 건수
4. 재현 가능한 커밋 또는 CI 실행 링크

문서 링크, 클래스 이름만 있는 빈 테스트, 로컬 생성물만으로는 검증 완료를 주장하지 않는다. 실패·제외 테스트가 있으면 그대로 기록한다.

## 2. 현재 구현 기준선

| 범위 | 현재 상태 | 확인 가능한 증거 | 아직 없는 것 |
|---|---|---|---|
| 빌드 기준선 | 부분 구현 | [build.gradle](../build.gradle)에 Java 17·Spring Boot 4.1.0, [Gradle wrapper](../gradle/wrapper/gradle-wrapper.properties)에 Gradle 9.5.1, [compose.yaml](../compose.yaml)에 MySQL 8.4 | Flyway·외부 연동·Spotless 구현 구성, 사용하지 않는 인증 의존성 정리 |
| 애플리케이션 시작점 | 검증됨 | [CoffeeOrderSystemApplication.java](../src/main/java/com/example/coffeeordersystem/CoffeeOrderSystemApplication.java), 2026-07-13 MySQL 연결 후 `./gradlew bootRun` 시작 성공 | 업무 API와 health endpoint |
| Spring context·설정 테스트 | 부분 검증 | [CoffeeOrderSystemApplicationTests.java](../src/test/java/com/example/coffeeordersystem/CoffeeOrderSystemApplicationTests.java)에 현재 2개 테스트 존재; 2026-07-13 기록은 `contextLoads()` 1개 중 1개 통과 | 현재 2개 전체의 최신 실행 증거와 업무 기능별 테스트 |
| 기능 구현 | 미구현 | 메인 소스에는 부트스트랩 클래스만 존재 | Menu, Point, Order, Idempotency, Outbox 전체 |
| 데이터베이스 | 부분 구현 | [compose.yaml](../compose.yaml)의 MySQL 8.4와 health check, [application.yaml](../src/main/resources/application.yaml)의 환경 변수 기반 연결 설정 | 테스트 DB 초기화·프로필, Flyway migration, 스키마, 초기 메뉴 |
| 검증 자동화 | 부분 구현 | 2026-07-13 `docker compose config`, MySQL health check, `./gradlew clean check`, `./gradlew bootRun` 통과 | 계층별 업무 테스트, Spotless, 테스트 DB·워커 격리, 성능 기준선, CI 증거 |

이 기준선은 문서상의 목표와 실제 저장소 상태를 분리하기 위한 것이다. 구현이 추가되면 아래 표의 상태와 증거를 같은 변경에서 갱신한다.

## 3. 기능 요구사항 추적

| 요구사항 | 요구 내용 | 설계 | API 계약 | 관련 ADR | 계획된 테스트 ID | 구현 상태 | 현재 증거 |
|---|---|---|---|---|---|---|---|
| [FR-01](./prd.md#fr-01-메뉴-목록-조회) | 메뉴 ID·이름·가격, ID 오름차순, Flyway 초기 메뉴 | [모듈러 모놀리스](./architecture.md#6-모듈러-모놀리스), [`menus`](./erd.md#42-menus) | [메뉴 목록 조회](./api-spec.md#4-메뉴-목록-조회) | [0006](./adr/0006-use-feature-oriented-modular-monolith.md), [0010](./adr/0010-test-against-mysql-with-docker-compose.md) | `IT-DB-001`, `AT-MENU-001` | 미구현 | 없음 — menu migration·API 없음 |
| [FR-02](./prd.md#fr-02-포인트-충전) | 요청 본문의 기존 사용자 ID, 양의 정수 충전, 덧셈 overflow 방지, UUID 멱등키 | [사용자 식별 경계](./architecture.md#8-사용자-식별-경계), [포인트 충전](./architecture.md#10-포인트-충전), [`users`](./erd.md#41-users), [`idempotency_records`](./erd.md#44-idempotency_records) | [포인트 충전](./api-spec.md#5-포인트-충전) | [0001](./adr/0001-use-database-pessimistic-locking-for-points.md), [0011](./adr/0011-return-temporary-unavailable-on-database-contention.md), [0015](./adr/0015-protect-mutations-with-idempotency-keys.md), [0016](./adr/0016-store-positive-point-balance-without-arbitrary-cap.md), [0020](./adr/0020-enforce-lifecycle-invariants-with-database-checks.md), [0022](./adr/0022-accept-user-id-without-authentication.md) | `UT-POINT-001`, `UT-POINT-002`, `UT-IDEM-001`, `UT-IDEM-002`, `IT-POINT-001`, `IT-IDEM-001`, `IT-IDEM-002`, `IT-RESILIENCE-001`, `AT-USER-001`, `AT-POINT-001`, `AT-POINT-002`, `AT-POINT-003`, `AT-POINT-004`, `CT-POINT-001`, `CT-POINT-002`, `CT-IDEM-001`, `CT-IDEM-002` | 미구현 | 없음 — Point·Idempotency 구현 없음 |
| [FR-03](./prd.md#fr-03-주문-및-결제) | 기존 사용자 ID, 단일 메뉴, 주문 시점 스냅샷, 차감·주문·Outbox 원자성, 멱등키 | [주문 트랜잭션](./architecture.md#11-주문-트랜잭션), [`orders`](./erd.md#43-orders) | [주문 및 결제](./api-spec.md#6-주문-및-결제) | [0001](./adr/0001-use-database-pessimistic-locking-for-points.md), [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md), [0009](./adr/0009-model-single-menu-orders-with-snapshots.md), [0011](./adr/0011-return-temporary-unavailable-on-database-contention.md), [0015](./adr/0015-protect-mutations-with-idempotency-keys.md), [0016](./adr/0016-store-positive-point-balance-without-arbitrary-cap.md), [0020](./adr/0020-enforce-lifecycle-invariants-with-database-checks.md), [0022](./adr/0022-accept-user-id-without-authentication.md) | `UT-POINT-003`, `UT-POINT-004`, `UT-IDEM-001`, `UT-IDEM-002`, `IT-ORDER-001`, `IT-ORDER-002`, `IT-ORDER-003`, `IT-RESILIENCE-001`, `AT-USER-001`, `AT-ORDER-001`, `AT-ORDER-002`, `AT-ORDER-003`, `AT-ORDER-004`, `AT-ORDER-005`, `CT-ORDER-001`, `CT-IDEM-001`, `CT-IDEM-002` | 미구현 | 없음 — Order 도메인·API·테스트 없음 |
| [FR-04](./prd.md#fr-04-주문-데이터-전송) | 주문 응답과 외부 장애 분리, 정상·backlog 없음 조건의 커밋 후 2초 이내 최초 HTTP 요청, 재시도와 최종 실패 보존 | [Outbox 전달](./architecture.md#12-outbox-전달), [`outbox_events`](./erd.md#45-outbox_events) | [외부 데이터 수집 API](./api-spec.md#10-외부-데이터-수집-api-계약) | [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md), [0014](./adr/0014-send-outbox-batches-asynchronously.md), [0017](./adr/0017-bound-first-outbox-attempt-latency.md), [0020](./adr/0020-enforce-lifecycle-invariants-with-database-checks.md) | `UT-OUTBOX-001`, `UT-OUTBOX-002`, `UT-OUTBOX-003`, `IT-OUTBOX-001`, `IT-OUTBOX-002`, `EXT-OUTBOX-001`, `EXT-OUTBOX-002`, `EXT-OUTBOX-003`, `EXT-OUTBOX-004`, `EXT-OUTBOX-005`, `EXT-OUTBOX-006`, `EXT-OUTBOX-007`, `EXT-OUTBOX-008`, `CT-OUTBOX-001`, `CT-OUTBOX-002` | 미구현 | 없음 — Outbox 저장소·워커·HTTP client 없음 |
| [FR-05](./prd.md#fr-05-인기-메뉴-조회) | 직전 7×24시간 `PAID` 주문, 상위 3개, 동률 ID 오름차순, 부족 시 존재 항목만 | [인기 메뉴 조회](./architecture.md#13-인기-메뉴-조회), [`orders`](./erd.md#43-orders) | [인기 메뉴 조회](./api-spec.md#7-인기-메뉴-조회) | [0004](./adr/0004-calculate-popular-menus-from-paid-orders.md) | `UT-POPULAR-001`, `UT-POPULAR-002`, `IT-POPULAR-001`, `IT-POPULAR-002`, `IT-POPULAR-003`, `AT-POPULAR-001`, `AT-POPULAR-002`; 후속 `PT-POPULAR-001` | 미구현 | 없음 — 집계 repository·API·테스트 없음 |

## 4. 비기능 요구사항 추적

| 요구사항 | 요구 내용 | 설계 | API 영향 | 관련 ADR | 계획된 테스트 ID | 구현 상태 | 현재 증거 |
|---|---|---|---|---|---|---|---|
| [NFR-01](./prd.md#nfr-01-데이터-일관성) | 공용 MySQL 정본, DB 제약, 실제 MySQL 검증, 외부 호출의 고객 트랜잭션 분리 | [데이터·트랜잭션 경계](./architecture.md#7-요청별-데이터트랜잭션-경계), [주요 불변식](./erd.md#9-트랜잭션-불변식) | 충전·주문·외부 전달 | [0001](./adr/0001-use-database-pessimistic-locking-for-points.md), [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md), [0005](./adr/0005-establish-java-spring-mysql-platform-baseline.md), [0010](./adr/0010-test-against-mysql-with-docker-compose.md), [0011](./adr/0011-return-temporary-unavailable-on-database-contention.md), [0015](./adr/0015-protect-mutations-with-idempotency-keys.md), [0016](./adr/0016-store-positive-point-balance-without-arbitrary-cap.md), [0018](./adr/0018-isolate-test-database-and-outbox-workers.md), [0020](./adr/0020-enforce-lifecycle-invariants-with-database-checks.md) | `IT-DB-001`, `IT-DB-002`, `IT-DB-003`, `IT-DB-004`, `IT-POINT-001`, `IT-ORDER-001`, `IT-ORDER-003`, `IT-OUTBOX-001`, `IT-RESILIENCE-001`, `QT-SCHEMA-001`, `QT-CONFIG-003` | 부분 구현 | MySQL 8.4 Compose와 개발 DB 연결 설정 존재; 테스트 DB·schema·트랜잭션·락 구현 없음 |
| [NFR-02](./prd.md#nfr-02-성능과-용량-측정) | Outbox 정상 최초 요청 2초는 첫 완료 게이트에서 검증하고 그 밖의 TBD 성능 기준선은 기능 완료 후 별도 측정 | [성능·확장성 검증](./architecture.md#15-성능확장성-검증) | 전체 API와 Outbox | [0017](./adr/0017-bound-first-outbox-attempt-latency.md) | 필수 `EXT-OUTBOX-008`; 후속 `PT-API-001`, `PT-LOCK-001`, `PT-POPULAR-001`, `PT-OUTBOX-001` | 측정 미실시 | 2초 계약만 확정; 성능 실행 결과 없음 |
| [NFR-03](./prd.md#nfr-03-확장성과-다중-인스턴스) | 로컬 상태·락 미사용, 공용 DB 기반 수평 확장, Outbox 분산 선점 | [런타임 구조](./architecture.md#5-런타임-구조), [점유와 fencing](./architecture.md#121-점유와-fencing) | 전체 API와 외부 이벤트 | [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md), [0006](./adr/0006-use-feature-oriented-modular-monolith.md), [0014](./adr/0014-send-outbox-batches-asynchronously.md), [0018](./adr/0018-isolate-test-database-and-outbox-workers.md), [0022](./adr/0022-accept-user-id-without-authentication.md) | `CT-APP-001`, `CT-POINT-001`, `CT-POINT-002`, `IT-OUTBOX-001`, `CT-OUTBOX-001`, `CT-OUTBOX-002`; 후속 `PT-LOCK-001`, `PT-OUTBOX-001` | 미구현 | 운영 목표 구조만 존재; 다중 인스턴스·워커 검증 없음 |
| [NFR-04](./prd.md#nfr-04-입력과-비밀정보-보호) | 엄격한 입력 검증, 환경 변수 비밀 주입, 내부 오류·자격 증명 미노출, 사용자 소유권 미검증 명시 | [사용자 식별 경계](./architecture.md#8-사용자-식별-경계), [관측성과 보안 운영](./architecture.md#17-관측성과-보안-운영) | 충전·주문과 공통 오류 | [0005](./adr/0005-establish-java-spring-mysql-platform-baseline.md), [0022](./adr/0022-accept-user-id-without-authentication.md) | `UT-API-001`, `AT-USER-001`, `AT-CONTRACT-001`, `QT-CONFIG-001`, `QT-OBS-001` | 부분 구현 | `.env` 비추적, `.env.example`, DB 환경 변수와 Spring import 존재; 사용자 ID·오류 경계는 미구현 |
| [NFR-05](./prd.md#nfr-05-장애-복구와-전달-신뢰성) | 주문과 외부 장애 분리, 정상 조건 2초 이내 최초 HTTP 요청, at-least-once, lease 복구, `FAILED` 보존 | [Outbox 전달](./architecture.md#12-outbox-전달), [장애 처리](./architecture.md#16-장애-처리) | 주문·외부 이벤트 | [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md), [0014](./adr/0014-send-outbox-batches-asynchronously.md), [0017](./adr/0017-bound-first-outbox-attempt-latency.md) | `IT-ORDER-001`, `IT-ORDER-003`, `IT-OUTBOX-002`, `EXT-OUTBOX-002`, `EXT-OUTBOX-003`, `EXT-OUTBOX-004`, `EXT-OUTBOX-005`, `EXT-OUTBOX-006`, `EXT-OUTBOX-007`, `EXT-OUTBOX-008` | 미구현 | Outbox 저장소·워커·HTTP client 없음 |
| [NFR-06](./prd.md#nfr-06-관찰-가능성) | 비밀정보를 제외한 상관 ID 로그와 API·DB·락·Outbox 지표, 장애 유형 구분 | [관측성과 보안 운영](./architecture.md#17-관측성과-보안-운영) | 전체 API와 워커 | [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md), [0013](./adr/0013-use-actuator-and-micrometer-for-observability.md) | `QT-CONFIG-001`, `QT-OBS-001`, `QT-OBS-002`, `EXT-OUTBOX-003`, `EXT-OUTBOX-004`; 후속 `PT-API-001`, `PT-LOCK-001`, `PT-OUTBOX-001` | 미구현 | 구조화 로그·메트릭·알림 구현 없음 |
| [NFR-07](./prd.md#nfr-07-시간-결정성) | 주입 가능한 Clock, 요청당 단일 기준 시각, DB·애플리케이션·테스트 UTC | [인기 메뉴 조회](./architecture.md#13-인기-메뉴-조회), [관측성과 보안 운영](./architecture.md#17-관측성과-보안-운영) | 주문·인기 메뉴·외부 이벤트 | [0004](./adr/0004-calculate-popular-menus-from-paid-orders.md), [0005](./adr/0005-establish-java-spring-mysql-platform-baseline.md) | `UT-OUTBOX-001`, `UT-POPULAR-001`, `IT-POPULAR-001`, `IT-OUTBOX-002`, `IT-TIME-001`, `AT-CONTRACT-002`, `EXT-OUTBOX-001`, `QT-CONFIG-002` | 미구현 | Clock 주입·UTC 구성·시간 경계 테스트 없음 |

## 5. 세부 정책 추적

다음 항목은 기존 요구사항을 구현 가능한 수준으로 구체화한 정책이다. 승인 여부는 관련 ADR 상태를 따른다. Outbox 최초 요청 2초 기준은 “실시간”을 검증 가능하게 구체화한 승인 목표이며, 그 밖의 정량 성능 목표는 아직 추가하지 않는다.

| 정책 ID | 확정 내용 | 반영 문서·API | 관련 ADR | 계획된 테스트 ID | 구현 상태 | 현재 증거 |
|---|---|---|---|---|---|---|
| `POL-PLATFORM-01` | Java 17, Spring Boot 4.1.0, Gradle 9.5.1, MySQL 8.4 LTS | [README](../README.md), [아키텍처](./architecture.md) | [0005](./adr/0005-establish-java-spring-mysql-platform-baseline.md) | `IT-DB-001`, `QT-CONFIG-002` | 구현됨·부분 검증 | Java·Boot·Gradle 빌드와 MySQL 8.4 Compose 실행 확인; Flyway 기반 DB 통합 테스트 없음 |
| `POL-IDEM-01` | MySQL upsert 후 잠금 조회로 키를 선점하고 업무 처리와 멱등 결과를 한 트랜잭션에 커밋 | [멱등 처리](./architecture.md#9-멱등-처리), [ERD](./erd.md#44-idempotency_records) | [0015](./adr/0015-protect-mutations-with-idempotency-keys.md) | `IT-ORDER-001`, `IT-ORDER-002`, `IT-ORDER-003`, `IT-IDEM-001`, `IT-IDEM-002`, `CT-IDEM-001`, `CT-IDEM-002` | 미구현 | 없음 |
| `POL-IDEM-02` | 완료 멱등 레코드는 최소 24시간 보존하며 현재 정리 기능은 구현하지 않음 | [ERD](./erd.md#44-idempotency_records) | [0015](./adr/0015-protect-mutations-with-idempotency-keys.md) | 예약: `IT-IDEM-003` | 현재 범위 제외 | 정리 기능 도입 시 구현·검증 |
| `POL-POINT-01` | 양의 signed `BIGINT` 충전만 허용하고 임의 상한 없이 덧셈 overflow를 `409 POINT_BALANCE_OVERFLOW`로 거절 | [포인트 충전](./architecture.md#10-포인트-충전), [API](./api-spec.md#5-포인트-충전), [ERD](./erd.md#41-users) | [0016](./adr/0016-store-positive-point-balance-without-arbitrary-cap.md) | `UT-POINT-001`, `UT-POINT-002`, `AT-POINT-001`, `AT-POINT-002`, `CT-POINT-001` | 미구현 | 없음 |
| `POL-OUTBOX-01` | `SKIP LOCKED`와 `claim_token`으로 선점; lease 30초; 이전 토큰의 늦은 갱신 차단 | [점유와 fencing](./architecture.md#121-점유와-fencing), [ERD](./erd.md#45-outbox_events) | [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md) | `IT-OUTBOX-001`, `IT-OUTBOX-002`, `CT-OUTBOX-001`, `CT-OUTBOX-002`, `EXT-OUTBOX-007` | 미구현 | 없음 |
| `POL-OUTBOX-02` | 즉시 첫 전송 후 1분·5분·30분 재시도, 총 4회; 연결 2초·요청 전체 5초 | [재시도 일정](./architecture.md#122-재시도-일정) | [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md), [0014](./adr/0014-send-outbox-batches-asynchronously.md) | `UT-OUTBOX-001`, `UT-OUTBOX-003`, `EXT-OUTBOX-002`, `EXT-OUTBOX-003`, `EXT-OUTBOX-004`, `EXT-OUTBOX-005` | 미구현 | 없음 |
| `POL-OUTBOX-03` | `2xx` 성공; 네트워크·timeout·408·429·5xx 재시도; redirect 없는 3xx·다른 4xx 즉시 `FAILED` | [HTTP 결과 분류](./architecture.md#123-http-결과-분류) | [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md) | `UT-OUTBOX-002`, `EXT-OUTBOX-003`, `EXT-OUTBOX-004`, `EXT-OUTBOX-005` | 미구현 | 없음 |
| `POL-OUTBOX-04` | `PUBLISHED`는 최소 30일 보존하고 `FAILED`는 자동 삭제하지 않으며 현재 정리 기능은 구현하지 않음 | [ERD](./erd.md#45-outbox_events) | [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md) | 예약: `IT-OUTBOX-003` | 현재 범위 제외 | 정리 기능 도입 시 구현·검증 |
| `POL-OUTBOX-05` | 활성 배치가 없을 때 기본 1초마다 오래된 due 이벤트 최대 50건을 선점해 비동기 병렬 전송 | [점유와 fencing](./architecture.md#121-점유와-fencing) | [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md), [0014](./adr/0014-send-outbox-batches-asynchronously.md) | `IT-OUTBOX-001`, `EXT-OUTBOX-002`, `CT-OUTBOX-001` | 미구현 | 없음 |
| `POL-OUTBOX-06` | 워커 정상·전송 가능한 기존 backlog 없음 조건에서 주문 커밋 후 2초 이내 최초 HTTP 요청 시작 | [Outbox 전달](./architecture.md#12-outbox-전달), [외부 API](./api-spec.md#10-외부-데이터-수집-api-계약) | [0017](./adr/0017-bound-first-outbox-attempt-latency.md) | 필수 `EXT-OUTBOX-002`, `EXT-OUTBOX-008`; 후속 `PT-OUTBOX-001` | 미구현 | 없음 |
| `POL-USER-01` | 충전·주문 본문의 양의 정수 `userId`로 기존 사용자를 식별하고 사용자 부재는 멱등 저장 없이 `404` | [사용자 식별 경계](./architecture.md#8-사용자-식별-경계), [API 공통 규칙](./api-spec.md#2-공통-규칙) | [0022](./adr/0022-accept-user-id-without-authentication.md) | `AT-USER-001`, `AT-POINT-001`, `AT-ORDER-001`, `CT-APP-001` | 미구현 | 없음 |
| `POL-OBS-01` | Actuator·Micrometer로 지표를 등록하고 HTTP에는 상세 정보를 숨긴 health만 노출 | [관측성과 보안 운영](./architecture.md#17-관측성과-보안-운영) | [0013](./adr/0013-use-actuator-and-micrometer-for-observability.md) | `QT-OBS-002`, `QT-CONFIG-001` | 미구현 | 없음 |
| `POL-POPULAR-01` | 주문 수는 결제 완료 주문에서 집계하고 이름·가격은 현재 `menus`에서 반환 | [인기 메뉴 조회](./architecture.md#13-인기-메뉴-조회), [API](./api-spec.md#7-인기-메뉴-조회) | [0004](./adr/0004-calculate-popular-menus-from-paid-orders.md) | `IT-POPULAR-001`, `IT-POPULAR-002`, `IT-POPULAR-003`, `AT-POPULAR-002` | 미구현 | 없음 |
| `POL-PERF-01` | Outbox 정상 최초 요청 2초만 첫 완료 게이트에서 검증하고 그 밖의 `PT-*` 기준선은 기능 완료 후 별도 측정 | [테스트 전략](./test-strategy.md#56-성능-기준선-테스트) | [0017](./adr/0017-bound-first-outbox-attempt-latency.md) | 필수 `EXT-OUTBOX-008`; 후속 `PT-API-001`, `PT-LOCK-001`, `PT-POPULAR-001`, `PT-OUTBOX-001` | 측정 미실시 | 2초 계약 외 수치 기준과 실행 결과 없음 |
| `POL-RUN-01` | Docker Compose는 MySQL만 실행하고 애플리케이션은 호스트에서 `./gradlew bootRun` | [테스트 환경](./test-strategy.md#41-환경-계약), [README](../README.md) | [0010](./adr/0010-test-against-mysql-with-docker-compose.md) | `IT-DB-001`, `QT-CONFIG-002` | 검증됨 | 2026-07-13 MySQL health check와 호스트 `bootRun` 시작 성공 |
| `POL-TEST-01` | 단일 Compose MySQL 안에서 개발·테스트 DB를 분리하고 일반 테스트의 Outbox 워커를 비활성화하며 테스트 소유 데이터만 정리 | [데이터 격리](./test-strategy.md#7-데이터-격리와-재현성) | [0010](./adr/0010-test-against-mysql-with-docker-compose.md), [0018](./adr/0018-isolate-test-database-and-outbox-workers.md) | `IT-DB-004`, `QT-CONFIG-003`, 전체 `IT-*`, `EXT-*`, `CT-*` | 미구현 | 테스트 DB·워커 프로필 없음 |
| `POL-SCHEMA-01` | 멱등·Outbox 상태별 필수·NULL 조합과 타입·횟수 범위를 MySQL CHECK로 강제 | [ERD 목표 제약](./erd.md#5-목표-제약-조건) | [0020](./adr/0020-enforce-lifecycle-invariants-with-database-checks.md) | `IT-DB-002`, `IT-DB-003`, `QT-SCHEMA-001` | 미구현 | Flyway schema 없음 |
| `POL-DEPS-01` | 애플리케이션 의존성은 Validation·Actuator·Flyway만, 빌드 도구는 Spotless·Java 포매터만 승인하고 Mock·비동기·polling은 JDK 사용 | [승인된 기술 기준선](./architecture.md#2-승인된-기술-기준선), [테스트 환경](./test-strategy.md#41-환경-계약) | [0005](./adr/0005-establish-java-spring-mysql-platform-baseline.md), [0013](./adr/0013-use-actuator-and-micrometer-for-observability.md), [0019](./adr/0019-use-spotless-as-format-gate.md), [0022](./adr/0022-accept-user-id-without-authentication.md) | `QT-DEPS-001`, `QT-CONFIG-001`, `QT-FORMAT-001` | 미구현 | 현재 build의 사용하지 않는 인증 의존성 정리와 승인 의존성·Spotless 추가 필요 |
| `POL-FORMAT-01` | `spotlessCheck`를 `check`에 포함하고 `spotlessApply`만 명시적으로 소스를 수정 | [테스트 실행 절차](./test-strategy.md#9-실행-절차) | [0019](./adr/0019-use-spotless-as-format-gate.md) | `QT-FORMAT-001` | 미구현 | Spotless 설정 없음 |

## 6. ADR 색인

활성 ADR은 프로젝트 소유자가 확인한 `승인됨` 상태이며 0002·0007과 인증 관련 0008·0012·0021은 후속 결정으로 대체됐다. 상태의 정본은 각 ADR 자체다.

| ADR | 결정 | 연결되는 요구·정책 |
|---|---|---|
| [0001](./adr/0001-use-database-pessimistic-locking-for-points.md) | 포인트 변경에 DB 비관적 락 사용 | FR-02, FR-03, NFR-01, NFR-03 |
| [0002](./adr/0002-protect-mutations-with-idempotency-keys.md) | 대체됨 — 기존 멱등키와 포인트 상한 결합 계약 | 0015가 대체 |
| [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md) | Transactional Outbox 전달 | FR-03, FR-04, NFR-01, NFR-03, NFR-05~06, POL-OUTBOX-01~05 |
| [0004](./adr/0004-calculate-popular-menus-from-paid-orders.md) | `PAID` 주문 직접 집계와 현재 메뉴 메타데이터 | FR-05, POL-POPULAR-01 |
| [0005](./adr/0005-establish-java-spring-mysql-platform-baseline.md) | Java·Spring·Gradle·MySQL 기술 기준선 | NFR-01, NFR-04, NFR-07, POL-PLATFORM-01 |
| [0006](./adr/0006-use-feature-oriented-modular-monolith.md) | 기능 중심 패키지의 모듈러 모놀리스 | FR-01~FR-05, NFR-03 |
| [0007](./adr/0007-store-current-point-balance-without-ledger.md) | 대체됨 — 상한이 있던 현재 포인트 잔액 모델 | 0016이 대체 |
| [0008](./adr/0008-use-stateless-jwt-access-token-authentication.md) | 대체됨 — JWT Access Token 기반 무상태 인증 | 0022가 대체 |
| [0009](./adr/0009-model-single-menu-orders-with-snapshots.md) | 단일 메뉴 주문과 주문 시점 스냅샷 | FR-03 |
| [0010](./adr/0010-test-against-mysql-with-docker-compose.md) | Docker Compose MySQL 기반 통합 테스트 | FR-01, NFR-01, POL-RUN-01 |
| [0011](./adr/0011-return-temporary-unavailable-on-database-contention.md) | DB 락 5초 timeout·deadlock의 `503` 처리 | FR-02, FR-03, NFR-01, NFR-03 |
| [0012](./adr/0012-use-spring-security-for-jwt.md) | 대체됨 — JWT 구현에 Spring Security 사용 | 0022가 대체 |
| [0013](./adr/0013-use-actuator-and-micrometer-for-observability.md) | 관측성에 Actuator와 Micrometer 사용 | NFR-02, NFR-06, POL-OBS-01 |
| [0014](./adr/0014-send-outbox-batches-asynchronously.md) | Outbox 배치를 비동기로 병렬 전송 | FR-04, NFR-03, NFR-05, POL-OUTBOX-02·05 |
| [0015](./adr/0015-protect-mutations-with-idempotency-keys.md) | 충전·주문 멱등키와 단일 트랜잭션 | FR-02, FR-03, POL-IDEM-01~02 |
| [0016](./adr/0016-store-positive-point-balance-without-arbitrary-cap.md) | 임의 상한 없는 현재 포인트 잔액 | FR-02, FR-03, NFR-01, POL-POINT-01 |
| [0017](./adr/0017-bound-first-outbox-attempt-latency.md) | 정상 조건의 커밋 후 2초 이내 Outbox 최초 HTTP 요청 | FR-04, NFR-05, POL-OUTBOX-06 |
| [0018](./adr/0018-isolate-test-database-and-outbox-workers.md) | 테스트 DB 분리와 일반 테스트의 Outbox 워커 비활성화 | NFR-01, NFR-03, POL-TEST-01 |
| [0019](./adr/0019-use-spotless-as-format-gate.md) | Spotless 포맷 검사와 명시적 자동 수정 | POL-DEPS-01, POL-FORMAT-01 |
| [0020](./adr/0020-enforce-lifecycle-invariants-with-database-checks.md) | 멱등·Outbox 수명주기 불변식의 MySQL CHECK | FR-02~04, NFR-01, POL-SCHEMA-01 |
| [0021](./adr/0021-configure-stateless-bearer-security-boundary.md) | 대체됨 — 무상태 Bearer REST 경계 | 0022가 대체 |
| [0022](./adr/0022-accept-user-id-without-authentication.md) | 인증 없이 요청 본문의 사용자 ID 사용 | FR-02~03, NFR-03~04, POL-USER-01, POL-DEPS-01 |

## 7. 제외 범위 보호선

다음 항목은 [PRD 제외 범위](./prd.md#7-제외-범위)를 그대로 보존한다. 구현 중 필요해 보이더라도 요청 없이 범위에 넣지 않는다.

| ID | 제외 항목 | 현재 처리 |
|---|---|---|
| `NG-01` | 프론트엔드와 관리자 화면 | 제외 유지 |
| `NG-02` | 메뉴 등록·수정·삭제 API | 제외 유지; 현재 메뉴 메타데이터 조회만 사용 |
| `NG-03` | 다품목 주문, 수량, 장바구니 | 제외 유지; 단일 메뉴 주문 모델 |
| `NG-04` | 주문 취소·환불 | 제외 유지 |
| `NG-05` | 포인트 원장 | 제외 유지; 현재 잔액 모델 |
| `NG-06` | 사용자 생성·관리, 회원가입·로그인과 인증·인가 | 제외 유지; 기존 사용자 ID를 요청 본문으로 입력 |
| `NG-07` | Redis·Kafka 기반 잠금 또는 집계 | 제외 유지; MySQL 기준 |
| `NG-08` | 실제 클라우드 배포 | 제외 유지; 운영 목표 구조만 문서화 |

## 8. 구현 시 갱신 절차

1. 구현 시작 전에 대상 FR·NFR·정책 ID와 [테스트 전략](./test-strategy.md)의 테스트 ID를 작업 범위에 적는다.
2. 기능 소스와 함께 정상 경로, 실패·경계 경로 테스트를 작성한다.
3. 이 표의 구현 상태를 바꾸고 구현·테스트 소스 링크를 증거 열에 추가한다.
4. 개발 DB와 분리된 테스트 데이터베이스의 실제 MySQL 8.4에서 관련 테스트와 전체 테스트를 실행한다.
5. `./gradlew test`, `./gradlew check`, `./gradlew build`, `./gradlew bootRun` 결과를 건수와 함께 기록한다.
6. 문서·ERD·Flyway·API가 달라졌는지 `QT-TRACE-001`, `QT-SCHEMA-001` 관점으로 확인한다.
7. 통과 증거까지 연결된 항목만 `검증됨`으로 바꾼다.

구현 중 기존 결정으로 답할 수 없는 스택, 데이터 모델, 트랜잭션 경계, 외부 전달 의미 같은 되돌리기 비싼 선택이 생기면 임의로 확정하지 않는다. 선택지와 영향을 사용자에게 먼저 보고하고 결정을 받은 뒤 ADR을 작성하거나 기존 ADR을 변경한다.

## 9. 제출 전 추적성 점검

- PRD의 FR-01~FR-05와 NFR-01~NFR-07이 모두 한 행씩 존재한다.
- 모든 테스트 ID가 테스트 전략에 정의돼 있고, 구현된 테스트 이름과 일치한다.
- `검증됨` 행마다 구현 소스, 테스트 소스, 성공 건수, 실행 환경 증거가 있다.
- README와 모든 문서·ADR 상대 링크가 유효하다.
- Flyway가 만든 MySQL 스키마와 ERD의 컬럼·제약·인덱스가 일치한다.
- 컨트롤러 요청·응답, 오류 코드, 외부 이벤트가 API 명세와 일치한다.
- 동시성 결과가 별도 커넥션·트랜잭션에서 검증됐다.
- 일반 테스트가 개발 DB에 접속하지 않고 Outbox 워커를 비활성화하며, Outbox 전용 테스트만 워커를 명시적으로 활성화했다.
- `spotlessCheck`가 `check`에 포함되고 포맷 불일치에서 실패한다.
- 성능 수치는 환경·데이터 규모·측정 구간과 함께 기록되고, 합의하지 않은 목표를 만들지 않았다.
- 새 아키텍처 결정이 사용자 선택 없이 문서나 코드에 숨어 들어가지 않았다.
