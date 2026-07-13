# 요구사항 추적성

## 1. 목적과 판정 기준

이 문서는 [PRD](./prd.md)의 기능·비기능 요구사항이 설계, API, ADR, 구현, 테스트에서 빠지지 않았는지 추적하는 작업대장이다. 설계 문서가 상세하더라도 실행 가능한 코드와 통과한 테스트가 없으면 구현 증거로 간주하지 않는다.

2026-07-12 현재 애플리케이션은 Spring Boot 부트스트랩 단계다. 아래 테스트 ID는 [테스트 전략](./test-strategy.md)에 배정한 **계획 식별자**이며, 테스트 소스와 통과 결과가 생기기 전까지 검증 증거가 아니다.

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
| 빌드 기준선 | 부분 구현 | [build.gradle](../build.gradle)에 Java 17·Spring Boot 4.1.0, [Gradle wrapper](../gradle/wrapper/gradle-wrapper.properties)에 Gradle 9.5.1, [compose.yaml](../compose.yaml)에 MySQL 8.4 | Flyway, JWT·외부 연동 구현 구성 |
| 애플리케이션 시작점 | 검증됨 | [CoffeeOrderSystemApplication.java](../src/main/java/com/example/coffeeordersystem/CoffeeOrderSystemApplication.java), 2026-07-13 MySQL 연결 후 `./gradlew bootRun` 시작 성공 | 업무 API와 health endpoint |
| Spring context smoke test | 검증됨 | [CoffeeOrderSystemApplicationTests.java](../src/test/java/com/example/coffeeordersystem/CoffeeOrderSystemApplicationTests.java)의 `contextLoads()` 1건, 2026-07-13 `./gradlew clean check`에서 1개 중 1개 통과 | 업무 기능별 테스트 |
| 기능 구현 | 미구현 | 메인 소스에는 부트스트랩 클래스만 존재 | Auth, Menu, Point, Order, Idempotency, Outbox 전체 |
| 데이터베이스 | 부분 구현 | [compose.yaml](../compose.yaml)의 MySQL 8.4와 health check, [application.yaml](../src/main/resources/application.yaml)의 환경 변수 기반 연결 설정 | Flyway migration, 스키마, 초기 메뉴 |
| 검증 자동화 | 부분 구현 | 2026-07-13 `docker compose config`, MySQL health check, `./gradlew clean check`, `./gradlew bootRun` 통과 | 계층별 업무 테스트, lint 도구, 성능 기준선, CI 증거 |

이 기준선은 문서상의 목표와 실제 저장소 상태를 분리하기 위한 것이다. 구현이 추가되면 아래 표의 상태와 증거를 같은 변경에서 갱신한다.

## 3. 기능 요구사항 추적

| 요구사항 | 요구 내용 | 설계 | API 계약 | 관련 ADR | 계획된 테스트 ID | 구현 상태 | 현재 증거 |
|---|---|---|---|---|---|---|---|
| [FR-01](./prd.md#fr-01-회원가입) | 이메일 유일성, BCrypt 단방향 해시, 원문 비밀번호 미저장·미로깅 | [모듈러 모놀리스](./architecture.md#6-모듈러-모놀리스), [`users`](./erd.md#41-users) | [회원가입](./api-spec.md#4-회원가입), 오류 코드 | [0006](./adr/0006-use-feature-oriented-modular-monolith.md), [0008](./adr/0008-use-stateless-jwt-access-token-authentication.md) | `UT-AUTH-001`, `AT-AUTH-001`, `AT-AUTH-002`, `CT-AUTH-001`, `QT-OBS-001` | 미구현 | 없음 — Auth 소스·테스트 없음 |
| [FR-02](./prd.md#fr-02-로그인) | 자격 증명 검증, 사용자 ID 기반 JWT, Access Token 30분, 보호 API 인증 | [인증 경계](./architecture.md#8-인증-경계) | [로그인](./api-spec.md#5-로그인), [공통 인증 규칙](./api-spec.md#2-공통-규칙) | [0008](./adr/0008-use-stateless-jwt-access-token-authentication.md) | `UT-AUTH-001`, `UT-AUTH-002`, `UT-AUTH-003`, `AT-AUTH-003`, `AT-AUTH-004`, `AT-AUTH-005`, `CT-APP-001` | 미구현 | 없음 — Security·JWT 구현 없음 |
| [FR-03](./prd.md#fr-03-메뉴-목록-조회) | 메뉴 ID·이름·가격, ID 오름차순, Flyway 초기 메뉴 | [모듈러 모놀리스](./architecture.md#6-모듈러-모놀리스), [`menus`](./erd.md#42-menus) | [메뉴 목록 조회](./api-spec.md#6-메뉴-목록-조회) | [0006](./adr/0006-use-feature-oriented-modular-monolith.md), [0010](./adr/0010-test-against-mysql-with-docker-compose.md) | `IT-DB-001`, `AT-MENU-001` | 미구현 | 없음 — menu migration·API 없음 |
| [FR-04](./prd.md#fr-04-포인트-충전) | 인증 사용자, 1~1,000,000P, 잔액 최대 10,000,000P, UUID 멱등키 | [포인트 충전](./architecture.md#10-포인트-충전), [`users`](./erd.md#41-users), [`idempotency_records`](./erd.md#44-idempotency_records) | [포인트 충전](./api-spec.md#7-포인트-충전) | [0001](./adr/0001-use-database-pessimistic-locking-for-points.md), [0002](./adr/0002-protect-mutations-with-idempotency-keys.md), [0007](./adr/0007-store-current-point-balance-without-ledger.md), [0011](./adr/0011-return-temporary-unavailable-on-database-contention.md) | `UT-POINT-001`, `UT-POINT-002`, `UT-IDEM-001`, `UT-IDEM-002`, `IT-POINT-001`, `IT-IDEM-001`, `IT-IDEM-002`, `IT-IDEM-003`, `IT-RESILIENCE-001`, `AT-POINT-001`, `AT-POINT-002`, `AT-POINT-003`, `AT-POINT-004`, `CT-POINT-001`, `CT-POINT-002`, `CT-IDEM-001`, `CT-IDEM-002` | 미구현 | 없음 — Point·Idempotency 구현 없음 |
| [FR-05](./prd.md#fr-05-주문-및-결제) | 단일 메뉴, 주문 시점 이름·가격 스냅샷, 잔액 비음수, 차감·주문·Outbox 원자성, 멱등키 | [주문 트랜잭션](./architecture.md#11-주문-트랜잭션), [`orders`](./erd.md#43-orders) | [주문 및 결제](./api-spec.md#8-주문-및-결제) | [0001](./adr/0001-use-database-pessimistic-locking-for-points.md), [0002](./adr/0002-protect-mutations-with-idempotency-keys.md), [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md), [0007](./adr/0007-store-current-point-balance-without-ledger.md), [0009](./adr/0009-model-single-menu-orders-with-snapshots.md), [0011](./adr/0011-return-temporary-unavailable-on-database-contention.md) | `UT-POINT-003`, `UT-POINT-004`, `UT-IDEM-001`, `UT-IDEM-002`, `IT-ORDER-001`, `IT-ORDER-002`, `IT-ORDER-003`, `IT-RESILIENCE-001`, `AT-ORDER-001`, `AT-ORDER-002`, `AT-ORDER-003`, `AT-ORDER-004`, `AT-ORDER-005`, `CT-ORDER-001`, `CT-IDEM-001`, `CT-IDEM-002` | 미구현 | 없음 — Order 도메인·API·테스트 없음 |
| [FR-06](./prd.md#fr-06-주문-데이터-전송) | 주문 응답과 외부 장애 분리, 즉시 첫 시도, 이벤트 필드, 재시도와 최종 실패 보존 | [Outbox 전달](./architecture.md#12-outbox-전달), [`outbox_events`](./erd.md#45-outbox_events) | [외부 데이터 수집 API](./api-spec.md#12-외부-데이터-수집-api-계약) | [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md) | `UT-OUTBOX-001`, `UT-OUTBOX-002`, `UT-OUTBOX-003`, `IT-OUTBOX-001`, `IT-OUTBOX-002`, `IT-OUTBOX-003`, `EXT-OUTBOX-001`, `EXT-OUTBOX-002`, `EXT-OUTBOX-003`, `EXT-OUTBOX-004`, `EXT-OUTBOX-005`, `EXT-OUTBOX-006`, `EXT-OUTBOX-007`, `CT-OUTBOX-001`, `CT-OUTBOX-002` | 미구현 | 없음 — Outbox 저장소·워커·HTTP client 없음 |
| [FR-07](./prd.md#fr-07-인기-메뉴-조회) | 직전 7×24시간 `PAID` 주문, 상위 3개, 동률 ID 오름차순, 부족 시 존재 항목만 | [인기 메뉴 조회](./architecture.md#13-인기-메뉴-조회), [`orders`](./erd.md#43-orders) | [인기 메뉴 조회](./api-spec.md#9-인기-메뉴-조회) | [0004](./adr/0004-calculate-popular-menus-from-paid-orders.md) | `UT-POPULAR-001`, `UT-POPULAR-002`, `IT-POPULAR-001`, `IT-POPULAR-002`, `IT-POPULAR-003`, `AT-POPULAR-001`, `AT-POPULAR-002`, `PT-POPULAR-001` | 미구현 | 없음 — 집계 repository·API·테스트 없음 |

## 4. 비기능 요구사항 추적

| 요구사항 | 요구 내용 | 설계 | API 영향 | 관련 ADR | 계획된 테스트 ID | 구현 상태 | 현재 증거 |
|---|---|---|---|---|---|---|---|
| [NFR-01](./prd.md#nfr-01-데이터-일관성) | 공용 MySQL 정본, DB 제약, 실제 MySQL 검증, 외부 호출의 고객 트랜잭션 분리 | [데이터·트랜잭션 경계](./architecture.md#7-요청별-데이터트랜잭션-경계), [주요 불변식](./erd.md#9-트랜잭션-불변식) | 회원가입·충전·주문·외부 전달 | [0001](./adr/0001-use-database-pessimistic-locking-for-points.md), [0002](./adr/0002-protect-mutations-with-idempotency-keys.md), [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md), [0005](./adr/0005-establish-java-spring-mysql-platform-baseline.md), [0010](./adr/0010-test-against-mysql-with-docker-compose.md), [0011](./adr/0011-return-temporary-unavailable-on-database-contention.md) | `IT-DB-001`, `IT-DB-002`, `IT-DB-003`, `IT-POINT-001`, `IT-ORDER-001`, `IT-ORDER-003`, `IT-OUTBOX-001`, `IT-RESILIENCE-001`, `QT-SCHEMA-001` | 부분 구현 | MySQL 8.4 Compose와 연결 설정 존재; schema·트랜잭션·락 구현 없음 |
| [NFR-02](./prd.md#nfr-02-성능과-용량-측정) | 목표치는 TBD로 유지하고 API·락·DB·인기 쿼리·Outbox·자원 지표를 조건과 함께 측정 | [성능·확장성 검증](./architecture.md#15-성능확장성-검증) | 전체 API와 Outbox | 없음 — 측정 절차이며 수치 결정 ADR이 아님 | `PT-API-001`, `PT-LOCK-001`, `PT-POPULAR-001`, `PT-OUTBOX-001` | 측정 미실시 | 임의 목표 없음; 성능 실행 결과 없음 |
| [NFR-03](./prd.md#nfr-03-확장성과-다중-인스턴스) | 로컬 세션·락 미사용, 공용 DB 기반 수평 확장, Outbox 분산 선점 | [런타임 구조](./architecture.md#5-런타임-구조), [점유와 fencing](./architecture.md#121-점유와-fencing) | 전체 API와 외부 이벤트 | [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md), [0006](./adr/0006-use-feature-oriented-modular-monolith.md), [0008](./adr/0008-use-stateless-jwt-access-token-authentication.md) | `CT-APP-001`, `CT-POINT-001`, `CT-POINT-002`, `IT-OUTBOX-001`, `CT-OUTBOX-001`, `CT-OUTBOX-002`, `PT-LOCK-001`, `PT-OUTBOX-001` | 미구현 | 운영 목표 구조만 존재; 다중 인스턴스·워커 검증 없음 |
| [NFR-04](./prd.md#nfr-04-보안) | BCrypt, 민감정보 미노출, 환경 변수 비밀 주입, 인증 주체 기반 소유권 | [인증 경계](./architecture.md#8-인증-경계), [관측성과 보안 운영](./architecture.md#17-관측성과-보안-운영) | 회원가입·로그인·보호 API | [0005](./adr/0005-establish-java-spring-mysql-platform-baseline.md), [0008](./adr/0008-use-stateless-jwt-access-token-authentication.md) | `UT-AUTH-001`, `UT-AUTH-003`, `UT-API-001`, `AT-AUTH-001`, `AT-AUTH-004`, `AT-AUTH-005`, `AT-SEC-001`, `AT-CONTRACT-001`, `QT-CONFIG-001`, `QT-OBS-001` | 미구현 | [application.yaml](../src/main/resources/application.yaml)에 DB 접속 환경 변수만 존재; 인증·JWT 비밀 주입 구현 없음 |
| [NFR-05](./prd.md#nfr-05-장애-복구와-전달-신뢰성) | 주문과 외부 장애 분리, at-least-once, lease 복구, `FAILED` 보존 | [Outbox 전달](./architecture.md#12-outbox-전달), [장애 처리](./architecture.md#16-장애-처리) | 주문·외부 이벤트 | [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md) | `IT-ORDER-001`, `IT-ORDER-003`, `IT-OUTBOX-002`, `IT-OUTBOX-003`, `EXT-OUTBOX-002`, `EXT-OUTBOX-003`, `EXT-OUTBOX-004`, `EXT-OUTBOX-005`, `EXT-OUTBOX-006`, `EXT-OUTBOX-007` | 미구현 | Outbox 저장소·워커·HTTP client 없음 |
| [NFR-06](./prd.md#nfr-06-관찰-가능성) | 민감정보를 제외한 상관 ID 로그와 API·DB·락·Outbox 지표, 장애 유형 구분 | [관측성과 보안 운영](./architecture.md#17-관측성과-보안-운영) | 전체 API와 워커 | [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md), [0008](./adr/0008-use-stateless-jwt-access-token-authentication.md) | `QT-CONFIG-001`, `QT-OBS-001`, `QT-OBS-002`, `EXT-OUTBOX-003`, `EXT-OUTBOX-004`, `PT-API-001`, `PT-LOCK-001`, `PT-OUTBOX-001` | 미구현 | 구조화 로그·메트릭·알림 구현 없음 |
| [NFR-07](./prd.md#nfr-07-시간-결정성) | 주입 가능한 Clock, 요청당 단일 기준 시각, DB·애플리케이션·테스트 UTC | [인기 메뉴 조회](./architecture.md#13-인기-메뉴-조회), [관측성과 보안 운영](./architecture.md#17-관측성과-보안-운영) | 로그인·주문·인기 메뉴·외부 이벤트 | [0004](./adr/0004-calculate-popular-menus-from-paid-orders.md), [0005](./adr/0005-establish-java-spring-mysql-platform-baseline.md) | `UT-AUTH-002`, `UT-OUTBOX-001`, `UT-POPULAR-001`, `IT-IDEM-003`, `IT-POPULAR-001`, `IT-OUTBOX-002`, `IT-OUTBOX-003`, `IT-TIME-001`, `AT-CONTRACT-002`, `EXT-OUTBOX-001`, `QT-CONFIG-002` | 미구현 | Clock 주입·UTC 구성·시간 경계 테스트 없음 |

## 5. 세부 정책 추적

다음 항목은 기존 요구사항을 구현 가능한 수준으로 구체화한 정책이다. 승인 여부는 관련 ADR 상태를 따르며, 새 요구사항이나 임의 성능 목표를 추가한 것이 아니다.

| 정책 ID | 확정 내용 | 반영 문서·API | 관련 ADR | 계획된 테스트 ID | 구현 상태 | 현재 증거 |
|---|---|---|---|---|---|---|
| `POL-PLATFORM-01` | Java 17, Spring Boot 4.1.0, Gradle 9.5.1, MySQL 8.4 LTS | [README](../README.md), [아키텍처](./architecture.md) | [0005](./adr/0005-establish-java-spring-mysql-platform-baseline.md) | `IT-DB-001`, `QT-CONFIG-002` | 구현됨·부분 검증 | Java·Boot·Gradle 빌드와 MySQL 8.4 Compose 실행 확인; Flyway 기반 DB 통합 테스트 없음 |
| `POL-IDEM-01` | 업무 처리와 멱등 레코드는 한 DB 트랜잭션; 예상 가능한 실패는 결과 저장 후 커밋, 인프라 오류는 전체 롤백 | [주문 트랜잭션](./architecture.md#11-주문-트랜잭션), [ERD](./erd.md#44-idempotency_records) | [0002](./adr/0002-protect-mutations-with-idempotency-keys.md) | `IT-ORDER-001`, `IT-ORDER-002`, `IT-ORDER-003`, `IT-IDEM-001`, `IT-IDEM-002`, `CT-IDEM-001`, `CT-IDEM-002` | 미구현 | 없음 |
| `POL-IDEM-02` | 완료 멱등 레코드는 최소 24시간 보존 후 삭제 가능, 자동 정리 없음 | [ERD](./erd.md#44-idempotency_records) | [0002](./adr/0002-protect-mutations-with-idempotency-keys.md) | `IT-IDEM-003` | 미구현 | 없음 |
| `POL-OUTBOX-01` | `SKIP LOCKED`와 `claim_token`으로 선점; lease 30초; 이전 토큰의 늦은 갱신 차단 | [점유와 fencing](./architecture.md#121-점유와-fencing), [ERD](./erd.md#45-outbox_events) | [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md) | `IT-OUTBOX-001`, `IT-OUTBOX-002`, `CT-OUTBOX-001`, `CT-OUTBOX-002`, `EXT-OUTBOX-007` | 미구현 | 없음 |
| `POL-OUTBOX-02` | 즉시 첫 전송 후 1분·5분·30분 재시도, 총 4회; 연결 2초·응답 5초 | [재시도 일정](./architecture.md#122-재시도-일정) | [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md) | `UT-OUTBOX-001`, `UT-OUTBOX-003`, `EXT-OUTBOX-002`, `EXT-OUTBOX-003`, `EXT-OUTBOX-004`, `EXT-OUTBOX-005` | 미구현 | 없음 |
| `POL-OUTBOX-03` | `2xx` 성공; 네트워크·timeout·408·429·5xx 재시도; redirect 없는 3xx·다른 4xx 즉시 `FAILED` | [HTTP 결과 분류](./architecture.md#123-http-결과-분류) | [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md) | `UT-OUTBOX-002`, `EXT-OUTBOX-003`, `EXT-OUTBOX-004`, `EXT-OUTBOX-005` | 미구현 | 없음 |
| `POL-OUTBOX-04` | `PUBLISHED`는 최소 30일 후 삭제 가능, `FAILED`는 자동 삭제하지 않음 | [ERD](./erd.md#45-outbox_events) | [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md) | `IT-OUTBOX-003` | 미구현 | 없음 |
| `POL-OUTBOX-05` | 기본 1초 폴링, 배치 50건, 오래된 due 이벤트 우선 | [점유와 fencing](./architecture.md#121-점유와-fencing) | [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md) | `IT-OUTBOX-001`, `CT-OUTBOX-001` | 미구현 | 없음 |
| `POL-POPULAR-01` | 주문 수는 결제 완료 주문에서 집계하고 이름·가격은 현재 `menus`에서 반환 | [인기 메뉴 조회](./architecture.md#13-인기-메뉴-조회), [API](./api-spec.md#9-인기-메뉴-조회) | [0004](./adr/0004-calculate-popular-menus-from-paid-orders.md) | `IT-POPULAR-001`, `IT-POPULAR-002`, `IT-POPULAR-003`, `AT-POPULAR-002` | 미구현 | 없음 |
| `POL-PERF-01` | 목표치는 `TBD`; p50/p95/p99, 처리량, lock wait, 인기 쿼리 실행 계획, Outbox backlog를 먼저 측정 | [테스트 전략](./test-strategy.md#56-성능-기준선-테스트) | 없음 — 측정 절차이며 새 구조 결정이 아님 | `PT-API-001`, `PT-LOCK-001`, `PT-POPULAR-001`, `PT-OUTBOX-001` | 측정 미실시 | 수치 기준과 실행 결과 없음 |
| `POL-RUN-01` | Docker Compose는 MySQL만 실행하고 애플리케이션은 호스트에서 `./gradlew bootRun` | [테스트 환경](./test-strategy.md#41-환경-계약), [README](../README.md) | [0010](./adr/0010-test-against-mysql-with-docker-compose.md) | `IT-DB-001`, `QT-CONFIG-002` | 검증됨 | 2026-07-13 MySQL health check와 호스트 `bootRun` 시작 성공 |

## 6. ADR 색인

현재 ADR은 모두 프로젝트 소유자가 확인한 `승인됨` 상태다. 상태의 정본은 각 ADR 자체다.

| ADR | 결정 | 연결되는 요구·정책 |
|---|---|---|
| [0001](./adr/0001-use-database-pessimistic-locking-for-points.md) | 포인트 변경에 DB 비관적 락 사용 | FR-04, FR-05, NFR-01, NFR-03 |
| [0002](./adr/0002-protect-mutations-with-idempotency-keys.md) | 충전·주문 멱등키와 단일 트랜잭션 | FR-04, FR-05, POL-IDEM-01~02 |
| [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md) | Transactional Outbox 전달 | FR-05, FR-06, NFR-01, NFR-03, NFR-05~06, POL-OUTBOX-01~05 |
| [0004](./adr/0004-calculate-popular-menus-from-paid-orders.md) | `PAID` 주문 직접 집계와 현재 메뉴 메타데이터 | FR-07, POL-POPULAR-01 |
| [0005](./adr/0005-establish-java-spring-mysql-platform-baseline.md) | Java·Spring·Gradle·MySQL 기술 기준선 | NFR-01, NFR-04, NFR-07, POL-PLATFORM-01 |
| [0006](./adr/0006-use-feature-oriented-modular-monolith.md) | 기능 중심 패키지의 모듈러 모놀리스 | FR-01~FR-07, NFR-03 |
| [0007](./adr/0007-store-current-point-balance-without-ledger.md) | 현재 포인트 잔액 모델과 원장 제외 | FR-04, FR-05, NFR-01 |
| [0008](./adr/0008-use-stateless-jwt-access-token-authentication.md) | JWT Access Token 기반 무상태 인증 | FR-01, FR-02, NFR-03, NFR-04, NFR-06 |
| [0009](./adr/0009-model-single-menu-orders-with-snapshots.md) | 단일 메뉴 주문과 주문 시점 스냅샷 | FR-05 |
| [0010](./adr/0010-test-against-mysql-with-docker-compose.md) | Docker Compose MySQL 기반 통합 테스트 | FR-03, NFR-01, POL-RUN-01 |
| [0011](./adr/0011-return-temporary-unavailable-on-database-contention.md) | DB 락 5초 timeout·deadlock의 `503` 처리 | FR-04, FR-05, NFR-01, NFR-03 |

## 7. 제외 범위 보호선

다음 항목은 [PRD 제외 범위](./prd.md#7-제외-범위)를 그대로 보존한다. 구현 중 필요해 보이더라도 요청 없이 범위에 넣지 않는다.

| ID | 제외 항목 | 현재 처리 |
|---|---|---|
| `NG-01` | 프론트엔드와 관리자 화면 | 제외 유지 |
| `NG-02` | 메뉴 등록·수정·삭제 API | 제외 유지; 현재 메뉴 메타데이터 조회만 사용 |
| `NG-03` | 다품목 주문, 수량, 장바구니 | 제외 유지; 단일 메뉴 주문 모델 |
| `NG-04` | 주문 취소·환불 | 제외 유지 |
| `NG-05` | 포인트 원장 | 제외 유지; 현재 잔액 모델 |
| `NG-06` | Refresh Token, 로그아웃 폐기, OAuth, 계정 복구 | 제외 유지; 30분 Access Token만 사용 |
| `NG-07` | Redis·Kafka 기반 잠금 또는 집계 | 제외 유지; MySQL 기준 |
| `NG-08` | 실제 클라우드 배포 | 제외 유지; 운영 목표 구조만 문서화 |

## 8. 구현 시 갱신 절차

1. 구현 시작 전에 대상 FR·NFR·정책 ID와 [테스트 전략](./test-strategy.md)의 테스트 ID를 작업 범위에 적는다.
2. 기능 소스와 함께 정상 경로, 실패·경계 경로 테스트를 작성한다.
3. 이 표의 구현 상태를 바꾸고 구현·테스트 소스 링크를 증거 열에 추가한다.
4. 실제 MySQL 8.4에서 관련 테스트와 전체 테스트를 실행한다.
5. `./gradlew test`, `./gradlew check`, `./gradlew build`, `./gradlew bootRun` 결과를 건수와 함께 기록한다.
6. 문서·ERD·Flyway·API가 달라졌는지 `QT-TRACE-001`, `QT-SCHEMA-001` 관점으로 확인한다.
7. 통과 증거까지 연결된 항목만 `검증됨`으로 바꾼다.

구현 중 기존 결정으로 답할 수 없는 스택, 데이터 모델, 트랜잭션 경계, 외부 전달 의미 같은 되돌리기 비싼 선택이 생기면 임의로 확정하지 않는다. 선택지와 영향을 사용자에게 먼저 보고하고 결정을 받은 뒤 ADR을 작성하거나 기존 ADR을 변경한다.

## 9. 제출 전 추적성 점검

- PRD의 FR-01~FR-07과 NFR-01~NFR-07이 모두 한 행씩 존재한다.
- 모든 테스트 ID가 테스트 전략에 정의돼 있고, 구현된 테스트 이름과 일치한다.
- `검증됨` 행마다 구현 소스, 테스트 소스, 성공 건수, 실행 환경 증거가 있다.
- README와 모든 문서·ADR 상대 링크가 유효하다.
- Flyway가 만든 MySQL 스키마와 ERD의 컬럼·제약·인덱스가 일치한다.
- 컨트롤러 요청·응답, 오류 코드, 외부 이벤트가 API 명세와 일치한다.
- 동시성 결과가 별도 커넥션·트랜잭션에서 검증됐다.
- 성능 수치는 환경·데이터 규모·측정 구간과 함께 기록되고, 합의하지 않은 목표를 만들지 않았다.
- 새 아키텍처 결정이 사용자 선택 없이 문서나 코드에 숨어 들어가지 않았다.
