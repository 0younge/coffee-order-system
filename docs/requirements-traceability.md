# 요구사항 추적성

## 1. 목적과 판정 기준

이 문서는 [PRD](./prd.md)의 기능·비기능 요구사항이 설계, API, ADR, 구현, 테스트에서 빠지지 않았는지 추적하는 작업대장이다. 설계 문서가 상세하더라도 실행 가능한 코드와 통과한 테스트가 없으면 구현 증거로 간주하지 않는다.

2026-07-16 현재 애플리케이션 기반선, MySQL 스키마, 메뉴 조회와 포인트 충전은 구현·검증됐고 나머지 업무 기능은 구현 중이다. 아래 테스트 ID 중 실행 증거에 연결되지 않은 항목은 아직 **계획 식별자**이며, 테스트 소스와 통과 결과가 생기기 전까지 검증 증거가 아니다.

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
| 빌드 기준선 | 검증됨 | [build.gradle](../build.gradle)의 승인 의존성·Spotless, [Gradle wrapper](../gradle/wrapper/gradle-wrapper.properties)의 Gradle 9.5.1, [compose.yaml](../compose.yaml)의 MySQL 8.4; [#1 증거](#기반선-검증-증거-github-1) | 업무 기능별 구현·테스트 |
| 애플리케이션 시작점 | 검증됨 | [CoffeeOrderSystemApplication.java](../src/main/java/com/example/coffeeordersystem/CoffeeOrderSystemApplication.java), UTC `Clock`, health 최소 공개; [#1 증거](#기반선-검증-증거-github-1) | 업무 API |
| Spring context·설정 테스트 | 검증됨 | 포인트 충전까지 포함한 MySQL 기반 전체 44개 테스트; [#1 증거](#기반선-검증-증거-github-1), [#2 증거](#메뉴-api-검증-증거-github-2), [#3 증거](#포인트-충전-검증-증거-github-3) | 후속 업무 기능별 테스트 |
| 기능 구현 | 구현 중 | 공통 HTTP 응답 경계, Menu 조회, Point 충전과 Idempotency 구현·검증 완료 | Order, Popular Menu, Outbox 업무 코드 |
| 데이터베이스 | 검증됨 | [V1 migration](../src/main/resources/db/migration/V1__create_schema_and_seed_reference_data.sql), [V2 migration](../src/main/resources/db/migration/V2__make_lifecycle_codes_case_sensitive.sql), [DatabaseMigrationTest.java](../src/test/java/com/example/coffeeordersystem/database/DatabaseMigrationTest.java); [#1 증거](#기반선-검증-증거-github-1) | 업무 트랜잭션·락·쿼리 |
| 검증 자동화 | 검증됨 | 테스트 DB·워커 격리, Spotless `check` 연결, MySQL 기반 전체 44개 테스트; [#1 증거](#기반선-검증-증거-github-1), [#2 증거](#메뉴-api-검증-증거-github-2), [#3 증거](#포인트-충전-검증-증거-github-3) | 후속 이슈의 계층별 업무·동시성·외부 연동 테스트와 CI 증거 |

이 기준선은 문서상의 목표와 실제 저장소 상태를 분리하기 위한 것이다. 구현이 추가되면 아래 표의 상태와 증거를 같은 변경에서 갱신한다.

### 기반선 검증 증거 (GitHub #1)

1. 구현: [build.gradle](../build.gradle), [compose.yaml](../compose.yaml), [application.yaml](../src/main/resources/application.yaml), [TimeConfiguration.java](../src/main/java/com/example/coffeeordersystem/config/TimeConfiguration.java), [Flyway V1](../src/main/resources/db/migration/V1__create_schema_and_seed_reference_data.sql), [Flyway V2](../src/main/resources/db/migration/V2__make_lifecycle_codes_case_sensitive.sql)
2. 테스트: `IT-DB-001`~`004`, `IT-TIME-001`, `QT-CONFIG-002`~`003`, `QT-DEPS-001`, `QT-FORMAT-001`, `QT-SCHEMA-001` — [DatabaseMigrationTest.java](../src/test/java/com/example/coffeeordersystem/database/DatabaseMigrationTest.java), [TimeConfigurationTest.java](../src/test/java/com/example/coffeeordersystem/config/TimeConfigurationTest.java), [BuildConfigurationTest.java](../src/test/java/com/example/coffeeordersystem/quality/BuildConfigurationTest.java)
3. 실행: 2026-07-16 MySQL 8.4 healthy에서 `docker compose config -q`, `./gradlew clean check build` 성공; 전체 18개, 성공 18개, 실패 0개, 제외 0개. `./gradlew bootRun` 시작 성공, `GET /actuator/health` 응답 `UP`
4. 재현 커밋: `8b2c204`(기반선), `058a441`(스키마·UTC 보완), `6a7049b`(로컬 UTC 회귀 방지), `319c2f7`(의존성·DB 행렬 검증); GitHub 작업 이슈 `#1`

### 메뉴 API 검증 증거 (GitHub #2)

1. 구현: [ApiResponse.java](../src/main/java/com/example/coffeeordersystem/common/api/ApiResponse.java), [GlobalExceptionHandler.java](../src/main/java/com/example/coffeeordersystem/common/error/GlobalExceptionHandler.java), [menu 패키지](../src/main/java/com/example/coffeeordersystem/menu)
2. 테스트: `UT-API-001`, `AT-MENU-001`, 메뉴 GET 범위의 `AT-CONTRACT-001` — [GlobalExceptionHandlerTest.java](../src/test/java/com/example/coffeeordersystem/common/error/GlobalExceptionHandlerTest.java), [MenuApiTest.java](../src/test/java/com/example/coffeeordersystem/menu/MenuApiTest.java)
3. 실행: 2026-07-16 MySQL 8.4 healthy에서 관련 테스트 4개와 `./gradlew clean check build` 성공; 전체 22개, 성공 22개, 실패 0개, 제외 0개. `./gradlew bootRun`에서 `GET /api/v1/menus`의 `MENUS_RETRIEVED`·ID 오름차순 응답과 `/actuator/health`의 `UP` 확인
4. 재현 커밋: `2866e8c`; GitHub 작업 이슈 `#2`

### 포인트 충전 검증 증거 (GitHub #3)

1. 구현: [GlobalExceptionHandler.java](../src/main/java/com/example/coffeeordersystem/common/error/GlobalExceptionHandler.java), [idempotency 패키지](../src/main/java/com/example/coffeeordersystem/idempotency), [point 패키지](../src/main/java/com/example/coffeeordersystem/point), [ADR 0025](./adr/0025-lock-user-before-idempotency-record.md)
2. 테스트: `UT-POINT-001`~`002`, `UT-IDEM-001`~`002`, `IT-POINT-001`~`002`, `IT-IDEM-001`~`002`, `IT-RESILIENCE-001`, `AT-USER-001`, `AT-POINT-001`~`004`, 충전 범위의 `AT-CONTRACT-001`, `CT-POINT-001`~`002`, `CT-IDEM-001`~`002` — [RequestHasherTest.java](../src/test/java/com/example/coffeeordersystem/idempotency/RequestHasherTest.java), [point 테스트 패키지](../src/test/java/com/example/coffeeordersystem/point)
3. 실행: 2026-07-16 MySQL 8.4 healthy에서 `./gradlew clean check build` 성공; 전체 44개, 성공 44개, 실패 0개, 제외 0개. 실제 사용자 행 락 대기·deadlock, 결과 저장 뒤 장애의 원자적 롤백, 같은 사용자와 서로 다른 사용자, 동일 멱등키 경쟁을 독립 트랜잭션으로 검증했다. `./gradlew bootRun`에서 `POST /api/v1/points/charge`의 100P→350P 충전과 동일 키 응답 재사용, `/actuator/health`의 `UP`을 확인했다.
4. 재현 커밋: `21595d4`(구현), `263eb95`(deadlock 롤백 검증), `26f81f2`(엄격 입력·원자성 보강); GitHub 작업 이슈 `#3`

## 3. 기능 요구사항 추적

| 요구사항 | 요구 내용 | 설계 | API 계약 | 관련 ADR | 계획된 테스트 ID | 구현 상태 | 현재 증거 |
|---|---|---|---|---|---|---|---|
| [FR-01](./prd.md#fr-01-메뉴-목록-조회) | 메뉴 ID·이름·가격, ID 오름차순, Flyway 초기 메뉴 | [모듈러 모놀리스](./architecture.md#6-모듈러-모놀리스), [`menus`](./erd.md#42-menus) | [메뉴 목록 조회](./api-spec.md#4-메뉴-목록-조회) | [0006](./adr/0006-use-feature-oriented-modular-monolith.md), [0010](./adr/0010-test-against-mysql-with-docker-compose.md) | `IT-DB-001`, `AT-MENU-001` | 검증됨 | Flyway 메뉴는 [#1 증거](#기반선-검증-증거-github-1), 현재 값·ID 오름차순·무상태 JSON 응답은 [#2 증거](#메뉴-api-검증-증거-github-2)로 검증 |
| [FR-02](./prd.md#fr-02-포인트-충전) | 요청 본문의 기존 사용자 ID, 과제·로컬 기준 사용자, 양의 정수 충전, 덧셈 overflow 방지, UUID 멱등키 | [사용자 식별 경계](./architecture.md#8-사용자-식별-경계), [포인트 충전](./architecture.md#10-포인트-충전), [`users`](./erd.md#41-users), [`idempotency_records`](./erd.md#44-idempotency_records) | [포인트 충전](./api-spec.md#5-포인트-충전) | [0001](./adr/0001-use-database-pessimistic-locking-for-points.md), [0011](./adr/0011-return-temporary-unavailable-on-database-contention.md), [0015](./adr/0015-protect-mutations-with-idempotency-keys.md), [0016](./adr/0016-store-positive-point-balance-without-arbitrary-cap.md), [0020](./adr/0020-enforce-lifecycle-invariants-with-database-checks.md), [0022](./adr/0022-accept-user-id-without-authentication.md), [0024](./adr/0024-seed-reference-user-for-local-execution.md), [0025](./adr/0025-lock-user-before-idempotency-record.md) | `UT-POINT-001`, `UT-POINT-002`, `UT-IDEM-001`, `UT-IDEM-002`, `IT-DB-001`, `IT-POINT-001`, `IT-POINT-002`, `IT-IDEM-001`, `IT-IDEM-002`, `IT-RESILIENCE-001`, `AT-USER-001`, `AT-POINT-001`, `AT-POINT-002`, `AT-POINT-003`, `AT-POINT-004`, `CT-POINT-001`, `CT-POINT-002`, `CT-IDEM-001`, `CT-IDEM-002` | 검증됨 | 스키마·기준 사용자는 [#1 증거](#기반선-검증-증거-github-1), 충전·멱등 업무와 동시성·롤백은 [#3 증거](#포인트-충전-검증-증거-github-3)로 검증 |
| [FR-03](./prd.md#fr-03-주문-및-결제) | 기존 사용자 ID, 단일 메뉴, 주문 시점 스냅샷, 차감·주문·Outbox 원자성, 멱등키 | [주문 트랜잭션](./architecture.md#11-주문-트랜잭션), [`orders`](./erd.md#43-orders) | [주문 및 결제](./api-spec.md#6-주문-및-결제) | [0001](./adr/0001-use-database-pessimistic-locking-for-points.md), [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md), [0009](./adr/0009-model-single-menu-orders-with-snapshots.md), [0011](./adr/0011-return-temporary-unavailable-on-database-contention.md), [0015](./adr/0015-protect-mutations-with-idempotency-keys.md), [0016](./adr/0016-store-positive-point-balance-without-arbitrary-cap.md), [0020](./adr/0020-enforce-lifecycle-invariants-with-database-checks.md), [0022](./adr/0022-accept-user-id-without-authentication.md), [0025](./adr/0025-lock-user-before-idempotency-record.md) | `UT-POINT-003`, `UT-POINT-004`, `UT-IDEM-001`, `UT-IDEM-002`, `IT-ORDER-001`, `IT-ORDER-002`, `IT-ORDER-003`, `IT-RESILIENCE-001`, `AT-USER-001`, `AT-ORDER-001`, `AT-ORDER-002`, `AT-ORDER-003`, `AT-ORDER-004`, `AT-ORDER-005`, `CT-ORDER-001`, `CT-IDEM-001`, `CT-IDEM-002` | 구현 중 | 주문·Outbox 스키마와 제약은 [#1 증거](#기반선-검증-증거-github-1)로 검증됨; 주문 트랜잭션·API·테스트 미구현 |
| [FR-04](./prd.md#fr-04-주문-데이터-전송) | 주문 응답과 외부 장애 분리, 정상·backlog 없음 조건의 커밋 후 2초 이내 최초 HTTP 요청, 현재 claim의 실패 횟수와 상태별 필드 정규화 | [Outbox 전달](./architecture.md#12-outbox-전달), [`outbox_events`](./erd.md#45-outbox_events) | [외부 데이터 수집 API](./api-spec.md#10-외부-데이터-수집-api-계약) | [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md), [0014](./adr/0014-send-outbox-batches-asynchronously.md), [0017](./adr/0017-bound-first-outbox-attempt-latency.md), [0020](./adr/0020-enforce-lifecycle-invariants-with-database-checks.md), [0023](./adr/0023-define-outbox-field-lifecycle.md) | `UT-OUTBOX-001`, `UT-OUTBOX-002`, `UT-OUTBOX-003`, `IT-OUTBOX-001`, `IT-OUTBOX-002`, `EXT-OUTBOX-001`, `EXT-OUTBOX-002`, `EXT-OUTBOX-003`, `EXT-OUTBOX-004`, `EXT-OUTBOX-005`, `EXT-OUTBOX-006`, `EXT-OUTBOX-007`, `EXT-OUTBOX-008`, `EXT-OUTBOX-009`, `CT-OUTBOX-001`, `CT-OUTBOX-002` | 구현 중 | Outbox 스키마·상태 제약은 [#1 증거](#기반선-검증-증거-github-1)로 검증됨; 저장소·워커·HTTP client 미구현 |
| [FR-05](./prd.md#fr-05-인기-메뉴-조회) | 직전 7×24시간 `PAID` 주문, 상위 3개, 동률 ID 오름차순, 부족 시 존재 항목만 | [인기 메뉴 조회](./architecture.md#13-인기-메뉴-조회), [`orders`](./erd.md#43-orders) | [인기 메뉴 조회](./api-spec.md#7-인기-메뉴-조회) | [0004](./adr/0004-calculate-popular-menus-from-paid-orders.md) | `UT-POPULAR-001`, `UT-POPULAR-002`, `IT-POPULAR-001`, `IT-POPULAR-002`, `IT-POPULAR-003`, `AT-POPULAR-001`, `AT-POPULAR-002`; 후속 `PT-POPULAR-001` | 구현 중 | 인기 집계 인덱스는 [#1 증거](#기반선-검증-증거-github-1)로 검증됨; 집계 repository·API·테스트 미구현 |

## 4. 비기능 요구사항 추적

| 요구사항 | 요구 내용 | 설계 | API 영향 | 관련 ADR | 계획된 테스트 ID | 구현 상태 | 현재 증거 |
|---|---|---|---|---|---|---|---|
| [NFR-01](./prd.md#nfr-01-데이터-일관성) | 공용 MySQL 정본, DB 제약, 실제 MySQL 검증, 외부 호출의 고객 트랜잭션 분리 | [데이터·트랜잭션 경계](./architecture.md#7-요청별-데이터트랜잭션-경계), [주요 불변식](./erd.md#9-트랜잭션-불변식) | 충전·주문·외부 전달 | [0001](./adr/0001-use-database-pessimistic-locking-for-points.md), [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md), [0005](./adr/0005-establish-java-spring-mysql-platform-baseline.md), [0010](./adr/0010-test-against-mysql-with-docker-compose.md), [0011](./adr/0011-return-temporary-unavailable-on-database-contention.md), [0015](./adr/0015-protect-mutations-with-idempotency-keys.md), [0016](./adr/0016-store-positive-point-balance-without-arbitrary-cap.md), [0018](./adr/0018-isolate-test-database-and-outbox-workers.md), [0020](./adr/0020-enforce-lifecycle-invariants-with-database-checks.md), [0025](./adr/0025-lock-user-before-idempotency-record.md) | `IT-DB-001`, `IT-DB-002`, `IT-DB-003`, `IT-DB-004`, `IT-POINT-001`, `IT-POINT-002`, `IT-ORDER-001`, `IT-ORDER-003`, `IT-OUTBOX-001`, `IT-RESILIENCE-001`, `QT-SCHEMA-001`, `QT-CONFIG-003` | 구현 중 | 테스트 DB·schema·DB 제약은 [#1 증거](#기반선-검증-증거-github-1), 충전의 트랜잭션·락·경합 롤백은 [#3 증거](#포인트-충전-검증-증거-github-3)로 검증됨; 주문·외부 호출 분리는 미구현 |
| [NFR-02](./prd.md#nfr-02-성능과-용량-측정) | Outbox 정상 최초 요청 2초는 첫 완료 게이트에서 검증하고 그 밖의 TBD 성능 기준선은 기능 완료 후 별도 측정 | [성능·확장성 검증](./architecture.md#15-성능확장성-검증) | 전체 API와 Outbox | [0017](./adr/0017-bound-first-outbox-attempt-latency.md) | 필수 `EXT-OUTBOX-008`; 후속 `PT-API-001`, `PT-LOCK-001`, `PT-POPULAR-001`, `PT-OUTBOX-001` | 측정 미실시 | 2초 계약만 확정; 성능 실행 결과 없음 |
| [NFR-03](./prd.md#nfr-03-확장성과-다중-인스턴스) | 로컬 상태·락 미사용, 공용 DB 기반 수평 확장, Outbox 분산 선점 | [런타임 구조](./architecture.md#5-런타임-구조), [점유와 fencing](./architecture.md#121-점유와-fencing) | 전체 API와 외부 이벤트 | [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md), [0006](./adr/0006-use-feature-oriented-modular-monolith.md), [0014](./adr/0014-send-outbox-batches-asynchronously.md), [0018](./adr/0018-isolate-test-database-and-outbox-workers.md), [0022](./adr/0022-accept-user-id-without-authentication.md), [0025](./adr/0025-lock-user-before-idempotency-record.md) | `CT-APP-001`, `CT-POINT-001`, `CT-POINT-002`, `IT-OUTBOX-001`, `CT-OUTBOX-001`, `CT-OUTBOX-002`; 후속 `PT-LOCK-001`, `PT-OUTBOX-001` | 부분 구현 | 공용 MySQL 행 락으로 같은 사용자의 충전을 직렬화하고 서로 다른 사용자는 독립 처리함을 [#3 증거](#포인트-충전-검증-증거-github-3)로 검증; 다중 Spring Context·Outbox 워커 검증은 미구현 |
| [NFR-04](./prd.md#nfr-04-입력과-비밀정보-보호) | 엄격한 입력 검증, 결정적인 오류 우선순위, 환경 변수 비밀 주입, 내부 오류·자격 증명 미노출, 사용자 소유권 미검증 명시 | [사용자 식별 경계](./architecture.md#8-사용자-식별-경계), [관측성과 보안 운영](./architecture.md#17-관측성과-보안-운영) | 충전·주문과 공통 오류 | [0005](./adr/0005-establish-java-spring-mysql-platform-baseline.md), [0022](./adr/0022-accept-user-id-without-authentication.md) | `UT-API-001`, `AT-USER-001`, `AT-CONTRACT-001`, `AT-CONTRACT-003`, `QT-CONFIG-001`, `QT-OBS-001` | 부분 구현 | 환경 변수·내부 예외 비노출은 [#1 증거](#기반선-검증-증거-github-1)·[#2 증거](#메뉴-api-검증-증거-github-2), 문자열 숫자 변환·알 수 없는 필드·필수 헤더·사용자 ID 등 충전 POST 입력은 [#3 증거](#포인트-충전-검증-증거-github-3)로 검증; 주문 POST 계약은 미구현 |
| [NFR-05](./prd.md#nfr-05-장애-복구와-전달-신뢰성) | 주문과 외부 장애 분리, 정상 조건 2초 이내 최초 HTTP 요청, at-least-once, lease 복구, 결정적인 `PUBLISHED`·`FAILED` 필드 | [Outbox 전달](./architecture.md#12-outbox-전달), [장애 처리](./architecture.md#16-장애-처리) | 주문·외부 이벤트 | [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md), [0014](./adr/0014-send-outbox-batches-asynchronously.md), [0017](./adr/0017-bound-first-outbox-attempt-latency.md), [0023](./adr/0023-define-outbox-field-lifecycle.md) | `IT-ORDER-001`, `IT-ORDER-003`, `IT-OUTBOX-002`, `EXT-OUTBOX-002`, `EXT-OUTBOX-003`, `EXT-OUTBOX-004`, `EXT-OUTBOX-005`, `EXT-OUTBOX-006`, `EXT-OUTBOX-007`, `EXT-OUTBOX-008`, `EXT-OUTBOX-009` | 미구현 | Outbox 저장소·워커·HTTP client 없음 |
| [NFR-06](./prd.md#nfr-06-관찰-가능성) | key-value 상관 로그, MVC·Hikari 기본 지표, DB 경합·Outbox 최소 counter·gauge, health만 공개 | [관측성과 보안 운영](./architecture.md#17-관측성과-보안-운영) | 전체 API와 워커 | [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md), [0013](./adr/0013-use-actuator-and-micrometer-for-observability.md) | `QT-CONFIG-001`, `QT-OBS-001`, `QT-OBS-002`, `EXT-OUTBOX-003`, `EXT-OUTBOX-004`; 후속 `PT-API-001`, `PT-LOCK-001`, `PT-OUTBOX-001` | 부분 구현 | 상세 정보 없는 health만 공개하고 [#1 증거](#기반선-검증-증거-github-1)로 `UP` 확인; key-value 로그·업무 메트릭 미구현 |
| [NFR-07](./prd.md#nfr-07-시간-결정성) | 주입 가능한 Clock, 요청당 단일 기준 시각, DB·애플리케이션·테스트 UTC | [인기 메뉴 조회](./architecture.md#13-인기-메뉴-조회), [관측성과 보안 운영](./architecture.md#17-관측성과-보안-운영) | 주문·인기 메뉴·외부 이벤트 | [0004](./adr/0004-calculate-popular-menus-from-paid-orders.md), [0005](./adr/0005-establish-java-spring-mysql-platform-baseline.md) | `UT-OUTBOX-001`, `UT-POPULAR-001`, `IT-POPULAR-001`, `IT-OUTBOX-002`, `IT-TIME-001`, `AT-CONTRACT-002`, `EXT-OUTBOX-001`, `QT-CONFIG-002` | 구현 중 | 주입 가능한 UTC `Clock`, JDBC·Jackson·JPA·DB UTC와 비 UTC JVM 저장을 [#1 증거](#기반선-검증-증거-github-1)로 검증; 업무 요청당 단일 시각 사용은 미구현 |

## 5. 세부 정책 추적

다음 항목은 기존 요구사항을 구현 가능한 수준으로 구체화한 정책이다. 승인 여부는 관련 ADR 상태를 따른다. Outbox 최초 요청 2초 기준은 “실시간”을 검증 가능하게 구체화한 승인 목표이며, 그 밖의 정량 성능 목표는 아직 추가하지 않는다.

| 정책 ID | 확정 내용 | 반영 문서·API | 관련 ADR | 계획된 테스트 ID | 구현 상태 | 현재 증거 |
|---|---|---|---|---|---|---|
| `POL-PLATFORM-01` | Java 17, Spring Boot 4.1.0, Gradle 9.5.1, MySQL 8.4 LTS | [README](../README.md), [아키텍처](./architecture.md) | [0005](./adr/0005-establish-java-spring-mysql-platform-baseline.md) | `IT-DB-001`, `QT-CONFIG-002` | 검증됨 | 문서·빌드·Compose·실제 MySQL Flyway 실행 일치를 [#1 증거](#기반선-검증-증거-github-1)로 검증 |
| `POL-IDEM-01` | 사용자 행 잠금 뒤 MySQL upsert·잠금 조회로 키를 선점하고 업무 처리와 멱등 결과를 한 트랜잭션에 커밋 | [멱등 처리](./architecture.md#9-멱등-처리), [ERD](./erd.md#44-idempotency_records) | [0015](./adr/0015-protect-mutations-with-idempotency-keys.md), [0025](./adr/0025-lock-user-before-idempotency-record.md) | `IT-ORDER-001`, `IT-ORDER-002`, `IT-ORDER-003`, `IT-IDEM-001`, `IT-IDEM-002`, `CT-IDEM-001`, `CT-IDEM-002` | 부분 구현 | 충전의 선점·결과 재사용·키 충돌·원자적 커밋은 [#3 증거](#포인트-충전-검증-증거-github-3)로 검증; 주문 적용은 미구현 |
| `POL-IDEM-02` | 완료 멱등 레코드는 최소 24시간 보존하며 현재 정리 기능은 구현하지 않음 | [ERD](./erd.md#44-idempotency_records) | [0015](./adr/0015-protect-mutations-with-idempotency-keys.md) | 예약: `IT-IDEM-003` | 현재 범위 제외 | 정리 기능 도입 시 구현·검증 |
| `POL-POINT-01` | 양의 signed `BIGINT` 충전만 허용하고 임의 상한 없이 덧셈 overflow를 `409 POINT_BALANCE_OVERFLOW`로 거절 | [포인트 충전](./architecture.md#10-포인트-충전), [API](./api-spec.md#5-포인트-충전), [ERD](./erd.md#41-users) | [0016](./adr/0016-store-positive-point-balance-without-arbitrary-cap.md) | `UT-POINT-001`, `UT-POINT-002`, `AT-POINT-001`, `AT-POINT-002`, `CT-POINT-001` | 검증됨 | 입력 범위·정확한 잔액 증가·overflow 상태 불변과 멱등 결과를 [#3 증거](#포인트-충전-검증-증거-github-3)로 검증 |
| `POL-OUTBOX-01` | `SKIP LOCKED`와 `claim_token`으로 선점; lease 30초; 이전 토큰의 늦은 갱신 차단 | [점유와 fencing](./architecture.md#121-점유와-fencing), [ERD](./erd.md#45-outbox_events) | [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md) | `IT-OUTBOX-001`, `IT-OUTBOX-002`, `CT-OUTBOX-001`, `CT-OUTBOX-002`, `EXT-OUTBOX-007` | 미구현 | 없음 |
| `POL-OUTBOX-02` | 즉시 첫 전송 후 1분·5분·30분 재시도, 현재 claim의 반영 실패마다 count 증가, 총 4회; 연결 2초·요청 전체 5초 | [재시도 일정](./architecture.md#122-재시도-일정) | [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md), [0014](./adr/0014-send-outbox-batches-asynchronously.md), [0023](./adr/0023-define-outbox-field-lifecycle.md) | `UT-OUTBOX-001`, `UT-OUTBOX-003`, `EXT-OUTBOX-002`, `EXT-OUTBOX-003`, `EXT-OUTBOX-004`, `EXT-OUTBOX-005` | 미구현 | 없음 |
| `POL-OUTBOX-03` | `2xx` 성공; 네트워크·timeout·408·429·5xx 재시도; redirect 없는 3xx·다른 4xx 즉시 `FAILED` | [HTTP 결과 분류](./architecture.md#123-http-결과-분류) | [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md) | `UT-OUTBOX-002`, `EXT-OUTBOX-003`, `EXT-OUTBOX-004`, `EXT-OUTBOX-005` | 미구현 | 없음 |
| `POL-OUTBOX-04` | `PUBLISHED`는 최소 30일 보존하고 `FAILED`는 자동 삭제하지 않으며 현재 정리 기능은 구현하지 않음 | [ERD](./erd.md#45-outbox_events) | [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md) | 예약: `IT-OUTBOX-003` | 현재 범위 제외 | 정리 기능 도입 시 구현·검증 |
| `POL-OUTBOX-05` | 활성 배치가 없을 때 기본 1초마다 오래된 due 이벤트 최대 50건을 선점해 비동기 병렬 전송 | [점유와 fencing](./architecture.md#121-점유와-fencing) | [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md), [0014](./adr/0014-send-outbox-batches-asynchronously.md) | `IT-OUTBOX-001`, `EXT-OUTBOX-002`, `CT-OUTBOX-001` | 미구현 | 없음 |
| `POL-OUTBOX-06` | 워커 정상·전송 가능한 기존 backlog 없음 조건에서 주문 커밋 후 2초 이내 최초 HTTP 요청 시작 | [Outbox 전달](./architecture.md#12-outbox-전달), [외부 API](./api-spec.md#10-외부-데이터-수집-api-계약) | [0017](./adr/0017-bound-first-outbox-attempt-latency.md) | 필수 `EXT-OUTBOX-002`, `EXT-OUTBOX-008`; 후속 `PT-OUTBOX-001` | 미구현 | 없음 |
| `POL-OUTBOX-07` | 재시도 중 마지막 오류 보존, `PUBLISHED` 오류·예약 정리, `FAILED` 마지막 오류 보존과 예약 정리 | [ERD 상태 전이](./erd.md#72-outbox-이벤트), [외부 API](./api-spec.md#10-외부-데이터-수집-api-계약) | [0023](./adr/0023-define-outbox-field-lifecycle.md) | `IT-DB-002`, `EXT-OUTBOX-003`, `EXT-OUTBOX-004`, `EXT-OUTBOX-005`, `EXT-OUTBOX-009` | 미구현 | 없음 |
| `POL-USER-01` | 충전·주문 본문의 양의 정수 `userId`로 기존 사용자를 식별하고 과제·로컬에는 ID 1·0P를 seed하며 사용자 부재는 멱등 저장 없이 `404` | [사용자 식별 경계](./architecture.md#8-사용자-식별-경계), [API 공통 규칙](./api-spec.md#2-공통-규칙) | [0022](./adr/0022-accept-user-id-without-authentication.md), [0024](./adr/0024-seed-reference-user-for-local-execution.md) | `IT-DB-001`, `AT-USER-001`, `AT-POINT-001`, `AT-ORDER-001`, `CT-APP-001` | 부분 구현 | 기준 사용자 seed는 [#1 증거](#기반선-검증-증거-github-1), 충전의 사용자 입력·부재·멱등 미저장은 [#3 증거](#포인트-충전-검증-증거-github-3)로 검증; 주문 적용은 미구현 |
| `POL-OBS-01` | MVC·Hikari 기본 지표, DB 경합·Outbox 최소 counter·gauge와 key-value 로그를 구현하고 HTTP에는 상세 정보를 숨긴 health만 노출 | [관측성과 보안 운영](./architecture.md#17-관측성과-보안-운영) | [0013](./adr/0013-use-actuator-and-micrometer-for-observability.md) | `QT-OBS-001`, `QT-OBS-002`, `QT-CONFIG-001` | 부분 구현 | 상세 정보 없는 health 공개와 `UP` 응답은 [#1 증거](#기반선-검증-증거-github-1)로 검증; 로그·업무 지표 미구현 |
| `POL-POPULAR-01` | 주문 수는 결제 완료 주문에서 집계하고 이름·가격은 현재 `menus`에서 반환 | [인기 메뉴 조회](./architecture.md#13-인기-메뉴-조회), [API](./api-spec.md#7-인기-메뉴-조회) | [0004](./adr/0004-calculate-popular-menus-from-paid-orders.md) | `IT-POPULAR-001`, `IT-POPULAR-002`, `IT-POPULAR-003`, `AT-POPULAR-002` | 미구현 | 없음 |
| `POL-PERF-01` | Outbox 정상 최초 요청 2초만 첫 완료 게이트에서 검증하고 그 밖의 `PT-*` 기준선은 기능 완료 후 별도 측정 | [테스트 전략](./test-strategy.md#56-성능-기준선-테스트) | [0017](./adr/0017-bound-first-outbox-attempt-latency.md) | 필수 `EXT-OUTBOX-008`; 후속 `PT-API-001`, `PT-LOCK-001`, `PT-POPULAR-001`, `PT-OUTBOX-001` | 측정 미실시 | 2초 계약 외 수치 기준과 실행 결과 없음 |
| `POL-RUN-01` | Docker Compose는 MySQL만 실행하고 애플리케이션은 호스트에서 `./gradlew bootRun` | [테스트 환경](./test-strategy.md#41-환경-계약), [README](../README.md) | [0010](./adr/0010-test-against-mysql-with-docker-compose.md) | `IT-DB-001`, `QT-CONFIG-002` | 검증됨 | 2026-07-16 MySQL healthy와 호스트 `bootRun`·health `UP`을 [#1 증거](#기반선-검증-증거-github-1)로 확인 |
| `POL-TEST-01` | 멱등 Compose 스크립트로 개발·테스트 DB를 준비하고 일반 테스트의 Outbox 워커를 비활성화하며 테스트 소유 데이터만 정리 | [데이터 격리](./test-strategy.md#7-데이터-격리와-재현성) | [0010](./adr/0010-test-against-mysql-with-docker-compose.md), [0018](./adr/0018-isolate-test-database-and-outbox-workers.md) | `IT-DB-004`, `QT-CONFIG-003`, 전체 `IT-*`, `EXT-*`, `CT-*` | 검증됨 | [Compose 초기화 스크립트](../docker/mysql/init/01-create-test-database.sh), 테스트 프로필과 개발 DB 비변경을 [#1 증거](#기반선-검증-증거-github-1)로 검증 |
| `POL-SCHEMA-01` | 멱등·Outbox 상태별 필수·NULL 조합과 타입·횟수 범위를 MySQL CHECK로 강제 | [ERD 목표 제약](./erd.md#5-목표-제약-조건) | [0020](./adr/0020-enforce-lifecycle-invariants-with-database-checks.md), [0023](./adr/0023-define-outbox-field-lifecycle.md) | `IT-DB-002`, `IT-DB-003`, `QT-SCHEMA-001` | 검증됨 | 상태별 조합·범위·대소문자·FK·UNIQUE·인덱스를 실제 MySQL에서 [#1 증거](#기반선-검증-증거-github-1)로 검증 |
| `POL-DEPS-01` | 기존 Web MVC·JPA·MySQL 외 추가 애플리케이션 의존성은 Validation·Actuator·Flyway만, 빌드 도구는 Spotless·Java 포매터만 승인하고 Mock·비동기·polling은 JDK 사용 | [승인된 기술 기준선](./architecture.md#2-승인된-기술-기준선), [테스트 환경](./test-strategy.md#41-환경-계약) | [0005](./adr/0005-establish-java-spring-mysql-platform-baseline.md), [0013](./adr/0013-use-actuator-and-micrometer-for-observability.md), [0019](./adr/0019-use-spotless-as-format-gate.md), [0022](./adr/0022-accept-user-id-without-authentication.md) | `QT-DEPS-001`, `QT-CONFIG-001`, `QT-FORMAT-001` | 검증됨 | 사용자 승인된 Spring Boot Flyway 통합 모듈을 포함한 정확한 목록과 미승인 인증 의존성 부재를 [#1 증거](#기반선-검증-증거-github-1)로 검증 |
| `POL-FORMAT-01` | `spotlessCheck`를 `check`에 포함하고 `spotlessApply`만 명시적으로 소스를 수정 | [테스트 실행 절차](./test-strategy.md#9-실행-절차) | [0019](./adr/0019-use-spotless-as-format-gate.md) | `QT-FORMAT-001` | 검증됨 | Spotless·Google Java Format과 `check` 연결을 [#1 증거](#기반선-검증-증거-github-1)로 검증 |

## 6. ADR 색인

활성 ADR은 프로젝트 소유자가 확인한 `승인됨` 상태이며 0002·0007과 인증 관련 0008·0012·0021은 후속 결정으로 대체됐다. ADR 0022는 0005의 Security 기준선, 0006의 Auth 모듈, 0015의 인증 실패 예외, 0018의 공유 JWT 키 조건도 부분 대체하고, ADR 0024는 0022의 기존 사용자 준비 절차 제외 조항만 부분 대체한다. 상태와 부분 대체 관계의 정본은 [ADR 목록](./adr/)이다.

| ADR | 결정 | 연결되는 요구·정책 |
|---|---|---|
| [0001](./adr/0001-use-database-pessimistic-locking-for-points.md) | 포인트 변경에 DB 비관적 락 사용 | FR-02, FR-03, NFR-01, NFR-03 |
| [0002](./adr/0002-protect-mutations-with-idempotency-keys.md) | 대체됨 — 기존 멱등키와 포인트 상한 결합 계약 | 0015가 대체 |
| [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md) | Transactional Outbox 전달 | FR-03, FR-04, NFR-01, NFR-03, NFR-05~06, POL-OUTBOX-01~05 |
| [0004](./adr/0004-calculate-popular-menus-from-paid-orders.md) | `PAID` 주문 직접 집계와 현재 메뉴 메타데이터 | FR-05, POL-POPULAR-01 |
| [0005](./adr/0005-establish-java-spring-mysql-platform-baseline.md) | Java·Spring·Gradle·MySQL 기술 기준선; Security 조항은 0022가 부분 대체 | NFR-01, NFR-04, NFR-07, POL-PLATFORM-01 |
| [0006](./adr/0006-use-feature-oriented-modular-monolith.md) | 기능 중심 패키지의 모듈러 모놀리스; Auth 모듈은 0022가 부분 대체 | FR-01~FR-05, NFR-03 |
| [0007](./adr/0007-store-current-point-balance-without-ledger.md) | 대체됨 — 상한이 있던 현재 포인트 잔액 모델 | 0016이 대체 |
| [0008](./adr/0008-use-stateless-jwt-access-token-authentication.md) | 대체됨 — JWT Access Token 기반 무상태 인증 | 0022가 대체 |
| [0009](./adr/0009-model-single-menu-orders-with-snapshots.md) | 단일 메뉴 주문과 주문 시점 스냅샷 | FR-03 |
| [0010](./adr/0010-test-against-mysql-with-docker-compose.md) | Docker Compose MySQL 기반 통합 테스트 | FR-01, NFR-01, POL-RUN-01 |
| [0011](./adr/0011-return-temporary-unavailable-on-database-contention.md) | DB 락 5초 timeout·deadlock의 `503` 처리 | FR-02, FR-03, NFR-01, NFR-03 |
| [0012](./adr/0012-use-spring-security-for-jwt.md) | 대체됨 — JWT 구현에 Spring Security 사용 | 0022가 대체 |
| [0013](./adr/0013-use-actuator-and-micrometer-for-observability.md) | 관측성에 Actuator와 Micrometer 사용 | NFR-02, NFR-06, POL-OBS-01 |
| [0014](./adr/0014-send-outbox-batches-asynchronously.md) | Outbox 배치를 비동기로 병렬 전송 | FR-04, NFR-03, NFR-05, POL-OUTBOX-02·05 |
| [0015](./adr/0015-protect-mutations-with-idempotency-keys.md) | 충전·주문 멱등키와 단일 트랜잭션; 인증 실패 예외는 0022가 부분 대체 | FR-02, FR-03, POL-IDEM-01~02 |
| [0016](./adr/0016-store-positive-point-balance-without-arbitrary-cap.md) | 임의 상한 없는 현재 포인트 잔액 | FR-02, FR-03, NFR-01, POL-POINT-01 |
| [0017](./adr/0017-bound-first-outbox-attempt-latency.md) | 정상 조건의 커밋 후 2초 이내 Outbox 최초 HTTP 요청 | FR-04, NFR-05, POL-OUTBOX-06 |
| [0018](./adr/0018-isolate-test-database-and-outbox-workers.md) | 테스트 DB 분리와 일반 테스트의 Outbox 워커 비활성화; 공유 JWT 키 조건은 0022가 부분 대체 | NFR-01, NFR-03, POL-TEST-01 |
| [0019](./adr/0019-use-spotless-as-format-gate.md) | Spotless 포맷 검사와 명시적 자동 수정 | POL-DEPS-01, POL-FORMAT-01 |
| [0020](./adr/0020-enforce-lifecycle-invariants-with-database-checks.md) | 멱등·Outbox 수명주기 불변식의 MySQL CHECK | FR-02~04, NFR-01, POL-SCHEMA-01 |
| [0021](./adr/0021-configure-stateless-bearer-security-boundary.md) | 대체됨 — 무상태 Bearer REST 경계 | 0022가 대체 |
| [0022](./adr/0022-accept-user-id-without-authentication.md) | 인증 없이 요청 본문의 사용자 ID 사용 | FR-02~03, NFR-03~04, POL-USER-01, POL-DEPS-01 |
| [0023](./adr/0023-define-outbox-field-lifecycle.md) | Outbox 상태별 필드 수명주기와 실패 횟수 | FR-04, NFR-05, POL-OUTBOX-02·07, POL-SCHEMA-01 |
| [0024](./adr/0024-seed-reference-user-for-local-execution.md) | 과제와 로컬 실행용 기준 사용자 seed | FR-02, POL-USER-01 |
| [0025](./adr/0025-lock-user-before-idempotency-record.md) | FK 교착 회피를 위한 사용자 행 선잠금 | FR-02, FR-03, NFR-01, NFR-03, POL-IDEM-01 |

## 7. 제외 범위 보호선

다음 항목은 [PRD 제외 범위](./prd.md#7-제외-범위)를 그대로 보존한다. 구현 중 필요해 보이더라도 요청 없이 범위에 넣지 않는다.

| ID | 제외 항목 | 현재 처리 |
|---|---|---|
| `NG-01` | 프론트엔드와 관리자 화면 | 제외 유지 |
| `NG-02` | 메뉴 등록·수정·삭제 API | 제외 유지; 현재 메뉴 메타데이터 조회만 사용 |
| `NG-03` | 다품목 주문, 수량, 장바구니 | 제외 유지; 단일 메뉴 주문 모델 |
| `NG-04` | 주문 취소·환불 | 제외 유지 |
| `NG-05` | 포인트 원장 | 제외 유지; 현재 잔액 모델 |
| `NG-06` | 사용자 생성·관리, 회원가입·로그인과 인증·인가 | 제외 유지; 기존 사용자 ID를 요청 본문으로 입력하고 과제·로컬 기준 사용자만 seed |
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
