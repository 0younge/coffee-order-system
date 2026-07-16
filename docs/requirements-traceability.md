# 요구사항 추적성

## 1. 목적과 판정 기준

이 문서는 [PRD](./prd.md)의 기능·비기능 요구사항이 설계, API, ADR, 구현, 테스트에서 빠지지 않았는지 추적하는 작업대장이다. 설계 문서가 상세하더라도 실행 가능한 코드와 통과한 테스트가 없으면 구현 증거로 간주하지 않는다.

2026-07-17 현재 애플리케이션 기반선, MySQL 스키마, 메뉴 조회, 포인트 충전, 주문·결제, 인기 메뉴 조회, Outbox 전달, 관측성·엄격한 API 계약과 다중 인스턴스 교차 경계는 구현·검증됐다. 기능 우선 계층형 구조와 제한된 Lombok 정책은 승인됐고 실제 패키지 이동·구조 테스트·빌드 반영은 구현 중이다. 아래 테스트 ID 중 실행 증거에 연결되지 않은 항목은 아직 **계획 식별자**이며, 테스트 소스와 통과 결과가 생기기 전까지 검증 증거가 아니다.

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
| 빌드 기준선 | 검증됨 | [build.gradle](../build.gradle)의 승인 의존성·Spotless, [Gradle wrapper](../gradle/wrapper/gradle-wrapper.properties)의 Gradle 9.5.1, [compose.yaml](../compose.yaml)과 [CI workflow](../.github/workflows/ci.yml)의 MySQL 8.4; [#1](#기반선-검증-증거-github-1)·[#16 증거](#fresh-mysql-ci-검증-증거-github-16) | 없음 |
| 애플리케이션 시작점 | 검증됨 | [CoffeeOrderSystemApplication.java](../src/main/java/com/example/coffeeordersystem/CoffeeOrderSystemApplication.java), UTC `Clock`, health 최소 공개, 조건부 Outbox 워커와 관측 로그·지표, 독립 웹 Context의 공용 DB 처리; [#1 증거](#기반선-검증-증거-github-1), [#6 증거](#outbox-전달-검증-증거-github-6), [#7 증거](#관측성과-api-계약-검증-증거-github-7), [#8 증거](#다중-인스턴스와-추적성-검증-증거-github-8) | 없음 |
| Spring context·설정 테스트 | 검증됨 | MySQL 기반 전체 115개 테스트; [#1 증거](#기반선-검증-증거-github-1), [#2 증거](#메뉴-api-검증-증거-github-2), [#3 증거](#포인트-충전-검증-증거-github-3), [#4 증거](#주문결제-검증-증거-github-4), [#5 증거](#인기-메뉴-검증-증거-github-5), [#6 증거](#outbox-전달-검증-증거-github-6), [#7 증거](#관측성과-api-계약-검증-증거-github-7), [#8 증거](#다중-인스턴스와-추적성-검증-증거-github-8), [#9 증거](#최종-통합-검증-증거-github-9), [#12 증거](#outbox-설정-경계-검증-증거-github-12), [#13 증거](#outbox-워커-복구와-배치-선점-검증-증거-github-13), [#14 증거](#http-오류-응답-검증-증거-github-14), [#15 증거](#주문-시각-정밀도-검증-증거-github-15), [#16 증거](#fresh-mysql-ci-검증-증거-github-16), [#17 증거](#문서와-하네스-동기화-검증-증거-github-17), [#18 증거](#최종-전체-리뷰-검증-증거-github-18), [#30 증거](#주문-계층-경계-증거-github-30), [#31 증거](#outbox-전달-계층-경계-증거-github-31), [#32 증거](#common-api오류-경계-증거-github-32), [#33 증거](#전체-구조-검증-증거-github-33) | 없음 |
| 기능 구현 | 검증됨 | 공통 HTTP 응답 경계, Menu·Popular Menu 조회, Point 충전·Idempotency, Order 결제·Outbox 원자 저장과 분산 전달 구현·검증 완료 | 현재 승인 기능 없음 |
| 기능 우선 계층·Lombok 리팩토링 | 구조 검증됨 | [ADR-0026](./adr/0026-refine-feature-first-layered-architecture.md), [ADR-0027](./adr/0027-use-lombok-with-restrictions.md), GitHub 추적 이슈 `#19`, [#20 기준선](#구조-리팩토링-기준선-증거-github-20); Lombok 빌드·소스와 구조 테스트 `#22`~`#25`, Idempotency·Menu·Point 계층 경계 `#26`~`#28`, Outbox 기록 경계 `#29`, [#30 Order 계층 경계](#주문-계층-경계-증거-github-30), [#31 Outbox 전달 경계](#outbox-전달-계층-경계-증거-github-31), [#32 Common 경계](#common-api오류-경계-증거-github-32), [#33 전체 구조 검증](#전체-구조-검증-증거-github-33) | 최종 통합 리뷰·원격 CI 증거 |
| 데이터베이스 | 검증됨 | [V1 migration](../src/main/resources/db/migration/V1__create_schema_and_seed_reference_data.sql), [V2 migration](../src/main/resources/db/migration/V2__make_lifecycle_codes_case_sensitive.sql), [V3 migration](../src/main/resources/db/migration/V3__optimize_outbox_claim.sql), [DatabaseMigrationTest.java](../src/test/java/com/example/coffeeordersystem/database/DatabaseMigrationTest.java), [OutboxStore.java](../src/main/java/com/example/coffeeordersystem/outbox/infrastructure/OutboxStore.java); [#1 증거](#기반선-검증-증거-github-1), [#6 증거](#outbox-전달-검증-증거-github-6), [#13 증거](#outbox-워커-복구와-배치-선점-검증-증거-github-13), [#16 증거](#fresh-mysql-ci-검증-증거-github-16) | 없음 |
| 검증 자동화 | 검증됨 | 테스트 DB·워커 격리, Spotless `check` 연결, MySQL·Mock HTTP·독립 웹 Context 기반 전체 115개 테스트와 문서 링크·ID·현재 범위 상태 검사; [#1 증거](#기반선-검증-증거-github-1), [#2 증거](#메뉴-api-검증-증거-github-2), [#3 증거](#포인트-충전-검증-증거-github-3), [#4 증거](#주문결제-검증-증거-github-4), [#5 증거](#인기-메뉴-검증-증거-github-5), [#6 증거](#outbox-전달-검증-증거-github-6), [#7 증거](#관측성과-api-계약-검증-증거-github-7), [#8 증거](#다중-인스턴스와-추적성-검증-증거-github-8), [#9 증거](#최종-통합-검증-증거-github-9), [#12 증거](#outbox-설정-경계-검증-증거-github-12), [#13 증거](#outbox-워커-복구와-배치-선점-검증-증거-github-13), [#14 증거](#http-오류-응답-검증-증거-github-14), [#15 증거](#주문-시각-정밀도-검증-증거-github-15), [#16 증거](#fresh-mysql-ci-검증-증거-github-16), [#17 증거](#문서와-하네스-동기화-검증-증거-github-17), [#18 증거](#최종-전체-리뷰-검증-증거-github-18), [#30 증거](#주문-계층-경계-증거-github-30), [#31 증거](#outbox-전달-계층-경계-증거-github-31), [#32 증거](#common-api오류-경계-증거-github-32), [#33 증거](#전체-구조-검증-증거-github-33) | 없음 |

이 기준선은 문서상의 목표와 실제 저장소 상태를 분리하기 위한 것이다. 구현이 추가되면 아래 표의 상태와 증거를 같은 변경에서 갱신한다.

### 구조 리팩토링 기준선 증거 (GitHub #20)

1. 기준 소스: 기능별 flat package, Controller→Service, Point→Idempotency와 Order→Idempotency·Menu·Point·Outbox 내부 타입 직접 import
2. 보존 계약: 공개 API 4개, 사용자 행 선잠금, 잠금 뒤 시간 확정, 충전·주문·멱등·Outbox 원자성, Outbox `SKIP LOCKED`·fencing·트랜잭션 밖 HTTP
3. 실행: 2026-07-16 MySQL 8.4 healthy에서 `./gradlew clean check build` 성공; 전체 103개, 성공 103개, 실패 0개, 오류 0개, 제외 0개. `./gradlew bootRun` 시작과 `GET /actuator/health`의 `UP` 확인
4. 재현 커밋: `6bf7f064019b8923ec4521a2f30a57557b50b0f7`; GitHub 작업 이슈 `#20`; 독립 리뷰 must·should·nit 0개

### 주문 계층 경계 증거 (GitHub #30)

1. 구현: [Order API](../src/main/java/com/example/coffeeordersystem/order/api), [OrderFacade.java](../src/main/java/com/example/coffeeordersystem/order/application/OrderFacade.java), [Order Entity](../src/main/java/com/example/coffeeordersystem/order/domain/Order.java), [OrderRepository.java](../src/main/java/com/example/coffeeordersystem/order/infrastructure/OrderRepository.java)
2. 테스트: `UT-ORDER-001`~`002`, 기존 `IT-ORDER-*`·`IT-TIME-002`·`AT-ORDER-*`·`CT-ORDER-001`, 교차 인스턴스·Outbox 원자성·구조 경계 — [OrderCommandTest.java](../src/test/java/com/example/coffeeordersystem/order/application/OrderCommandTest.java), [OrderTest.java](../src/test/java/com/example/coffeeordersystem/order/domain/OrderTest.java), [OrderFacadeTest.java](../src/test/java/com/example/coffeeordersystem/order/application/OrderFacadeTest.java), [OrderConcurrencyTest.java](../src/test/java/com/example/coffeeordersystem/order/OrderConcurrencyTest.java), [LayeredArchitectureTest.java](../src/test/java/com/example/coffeeordersystem/quality/LayeredArchitectureTest.java)
3. 실행: 2026-07-17 MySQL 8.4 healthy에서 직접 관련 테스트 28개 성공, 실패·제외 0개. `./gradlew clean check build` 성공; 전체 114개, 성공 114개, 실패·오류·제외 0개. `./gradlew bootRun` 시작과 `GET /actuator/health`의 `UP`을 확인했다.
4. 재현 커밋: `bd6a0a2`(Order 계층 경계와 구조·회귀 테스트), `4946080`·`e82b077`(문서 추적성과 전용 증거), `0f0bb5b`(기존 스냅샷 허용 범위 보존); GitHub 작업 이슈 `#30`

### Outbox 전달 계층 경계 증거 (GitHub #31)

1. 구현: [OutboxDeliveryFacade.java](../src/main/java/com/example/coffeeordersystem/outbox/application/OutboxDeliveryFacade.java), [Outbox domain](../src/main/java/com/example/coffeeordersystem/outbox/domain), [OutboxWorker.java](../src/main/java/com/example/coffeeordersystem/outbox/infrastructure/OutboxWorker.java), [OutboxStore.java](../src/main/java/com/example/coffeeordersystem/outbox/infrastructure/OutboxStore.java), [OutboxHttpSender.java](../src/main/java/com/example/coffeeordersystem/outbox/infrastructure/OutboxHttpSender.java)
2. 테스트: `UT-OUTBOX-001`~`004`, `IT-OUTBOX-001`·`002`·`004`, `EXT-OUTBOX-001`~`009`, `CT-OUTBOX-001`~`002`, Outbox 관측성과 구조 경계 — [OutboxDeliveryFacadeTest.java](../src/test/java/com/example/coffeeordersystem/outbox/application/OutboxDeliveryFacadeTest.java), [Outbox domain 테스트](../src/test/java/com/example/coffeeordersystem/outbox/domain), [Outbox infrastructure 테스트](../src/test/java/com/example/coffeeordersystem/outbox/infrastructure), [LayeredArchitectureTest.java](../src/test/java/com/example/coffeeordersystem/quality/LayeredArchitectureTest.java)
3. 실행: 2026-07-17 MySQL 8.4 healthy와 Mock HTTP API에서 직접 관련 테스트 43개 성공, 실패·제외 0개. `./gradlew clean check build` 성공; 전체 114개, 성공 114개, 실패·오류·제외 0개. `./gradlew bootRun` 시작과 `GET /actuator/health`의 `UP`을 확인했다.
4. 재현 커밋: `0af5685`(Outbox 전달 계층과 구조·회귀 테스트), `5392f74`(문서 추적성), `da36903`(전송 실패 해석의 Infrastructure 격리와 domain 구조 규칙); GitHub 작업 이슈 `#31`

### Common API·오류 경계 증거 (GitHub #32)

1. 구현: [Common API](../src/main/java/com/example/coffeeordersystem/common/api), [Common error](../src/main/java/com/example/coffeeordersystem/common/error), [Common observability](../src/main/java/com/example/coffeeordersystem/common/observability); 기존 코드를 승인 경계 밖으로 추가 이동하지 않고 Common의 기능 독립성과 최소 하위 패키지를 구조 테스트로 고정
2. 테스트: `UT-API-001`, `UT-IDEM-003`, `AT-CONTRACT-004`, `QT-OBS-001`~`002`, API 회귀와 `QT-ARCH-001` — [Common 테스트](../src/test/java/com/example/coffeeordersystem/common), [LayeredArchitectureTest.java](../src/test/java/com/example/coffeeordersystem/quality/LayeredArchitectureTest.java)
3. 실행: 2026-07-17 MySQL 8.4 healthy에서 직접 관련 테스트 36개 성공, 실패·오류·제외 0개. `./gradlew clean check build` 성공; 전체 115개, 성공 115개, 실패·오류·제외 0개. `./gradlew bootRun` 시작과 `GET /actuator/health`의 `UP`을 확인했다.
4. 재현 커밋: `c82e32f`(Common 독립성·최소 경계 구조 테스트), `2987302`(구조 문서와 실행 증거), `6b14ce3`(Common 루트 소스 차단과 결합 강제 제거); GitHub 작업 이슈 `#32`

### 전체 구조 검증 증거 (GitHub #33)

1. 구현: [LayeredArchitectureTest.java](../src/test/java/com/example/coffeeordersystem/quality/LayeredArchitectureTest.java)가 승인된 최상위·기능 계층 위치, Controller의 자기 Facade 단일 주입, API·Application·Domain 독립성, cross-feature Application-only 참조, 기능 순환 부재, 중복 Service 부재와 제한된 Lombok 정책을 전체 소스에 검사
2. 우회 검증: 정적 import, 완전 수식 참조, 기능별 flat 소스, 전역 기술 패키지, API→Domain·다른 기능 Application, cross-feature flat 참조, Controller→Service 합성 사례가 구조 규칙에서 실패함을 검증
3. 실행: 2026-07-17 품질·구조 관련 테스트 13개 성공, 실패·오류·제외 0개. MySQL 8.4 healthy에서 `./gradlew clean check build` 성공; 전체 115개, 성공 115개, 실패·오류·제외 0개. `./gradlew bootRun` 시작과 `GET /actuator/health`의 `UP`을 확인했다. API 명세·ERD·Flyway V1~V3 해시는 리팩토링 기준선과 같다.
4. 재현 커밋: `816a0ac`(전체 소스 구조·우회 검증 완성), `278b217`(구조 문서·추적성 동기화), `4c4540b`(모든 Controller 형태와 인스턴스 의존성 우회 차단); GitHub 작업 이슈 `#33`

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
4. 재현 커밋: `21595d4`(구현), `263eb95`(deadlock 롤백 검증), `26f81f2`(엄격 입력·원자성 보강), `fd77869`·`e13860a`(금액 오류 코드 정합화); GitHub 작업 이슈 `#3`

### 주문·결제 검증 증거 (GitHub #4)

1. 구현: [order 패키지](../src/main/java/com/example/coffeeordersystem/order), [Outbox 이벤트 기록 계약](../src/main/java/com/example/coffeeordersystem/outbox/application/OutboxEventAppender.java), [Point 결제 계약](../src/main/java/com/example/coffeeordersystem/point/application/PointPaymentFacade.java), [Menu 조회 계약](../src/main/java/com/example/coffeeordersystem/menu/application/MenuQueryFacade.java)
2. 테스트: `UT-POINT-003`~`004`, `IT-ORDER-001`~`003`, `IT-IDEM-001`~`002`, `AT-USER-001`, `AT-ORDER-001`~`005`, 주문 범위의 엄격 입력·오류 우선순위, `CT-ORDER-001`, `CT-IDEM-001`~`002` — [order 테스트 패키지](../src/test/java/com/example/coffeeordersystem/order), [PointAccountTest.java](../src/test/java/com/example/coffeeordersystem/point/domain/PointAccountTest.java)
3. 실행: 2026-07-16 MySQL 8.4 healthy에서 `./gradlew clean check build` 성공; 전체 57개, 성공 57개, 실패 0개, 제외 0개. 주문 성공·메뉴 부재·잔액 부족·멱등 재사용과 충돌, Outbox 저장 뒤 장애의 전체 롤백, 같은 사용자의 동시 주문과 동일 키 경쟁을 독립 트랜잭션으로 검증했다. `./gradlew bootRun`에서 `/actuator/health`의 `UP`, 최초 주문과 동일 키 재요청의 `201 ORDER_PAID`, 최종 잔액 1,000P와 주문·Outbox·멱등 각 1건을 확인했다.
4. 재현 커밋: `9928945`(주문·결제 원자성), `2fee44e`(모듈 경계·계약 검증 보강); GitHub 작업 이슈 `#4`

### 인기 메뉴 검증 증거 (GitHub #5)

1. 구현: [MenuController.java](../src/main/java/com/example/coffeeordersystem/menu/api/MenuController.java), [MenuQueryFacade.java](../src/main/java/com/example/coffeeordersystem/menu/application/MenuQueryFacade.java), [PopularMenuRepository.java](../src/main/java/com/example/coffeeordersystem/menu/infrastructure/PopularMenuRepository.java)
2. 테스트: `UT-POPULAR-001`~`002`, `IT-POPULAR-001`~`003`, `AT-POPULAR-001`~`002` — [PopularMenuWindowTest.java](../src/test/java/com/example/coffeeordersystem/menu/domain/PopularMenuWindowTest.java), [PopularMenuRankingTest.java](../src/test/java/com/example/coffeeordersystem/menu/application/PopularMenuRankingTest.java), [PopularMenuApiTest.java](../src/test/java/com/example/coffeeordersystem/menu/PopularMenuApiTest.java). 현재 스키마는 `PAID`만 허용하므로 주문 상태를 확장할 때 비-`PAID` 제외 fixture를 함께 추가한다.
3. 실행: 2026-07-16 MySQL 8.4 healthy에서 `./gradlew clean check build` 성공; 전체 63개, 성공 63개, 실패 0개, 제외 0개. 고정 `Clock`의 7일 하한 포함·상한 제외, 0개·3개 미만·상위 3개, 동률 메뉴 ID 순서, 현재 메뉴 메타데이터, 전체 업무 테이블 무변경을 검증했다. `./gradlew bootRun`에서 `/actuator/health`의 `UP`, `GET /api/v1/menus/popular`의 `200 POPULAR_MENUS_RETRIEVED`, 주문 수 `3·3·1`과 rank `1~3`을 확인했다.
4. 재현 커밋: `71dc2d7`(인기 메뉴 조회), `ca6e0ef`(무상태 검증 보강); GitHub 작업 이슈 `#5`

### Outbox 전달 검증 증거 (GitHub #6)

1. 구현: [OutboxDeliveryFacade.java](../src/main/java/com/example/coffeeordersystem/outbox/application/OutboxDeliveryFacade.java), [OutboxStore.java](../src/main/java/com/example/coffeeordersystem/outbox/infrastructure/OutboxStore.java), [OutboxWorker.java](../src/main/java/com/example/coffeeordersystem/outbox/infrastructure/OutboxWorker.java), [OutboxHttpSender.java](../src/main/java/com/example/coffeeordersystem/outbox/infrastructure/OutboxHttpSender.java), [OutboxWorkerConfiguration.java](../src/main/java/com/example/coffeeordersystem/outbox/infrastructure/OutboxWorkerConfiguration.java)
2. 테스트: `UT-OUTBOX-001`~`003`, `IT-OUTBOX-001`~`002`, `EXT-OUTBOX-001`~`009`, `CT-OUTBOX-001`~`002`, 외부 주소의 `QT-CONFIG-001` — [outbox 테스트 패키지](../src/test/java/com/example/coffeeordersystem/outbox)
3. 실행: 2026-07-16 MySQL 8.4 healthy와 JDK Mock HTTP API에서 `./gradlew clean check build` 성공; 전체 82개, 성공 82개, 실패 0개, 제외 0개. 기본 1초 폴링의 커밋 후 2초 이내 최초 요청, 단일 활성 배치의 병렬 전송, 다중 워커 `SKIP LOCKED`, 30초 lease 복구와 fencing, 동일 `eventId` 재시도, HTTP 분류·timeout·필드 정규화를 검증했다. 독립 리뷰어 2명의 must·should는 최종 0개였다. 워커를 해당 프로세스에서만 비활성화한 `./gradlew bootRun`에서 `/actuator/health`의 `UP`과 메뉴 API 응답을 확인했고, 워커 활성 시작·전송 경로는 Mock API Spring 통합 테스트로 확인했다.
4. 재현 커밋: `bfb7b71`(분산 전달 워커), `3e30e45`(기본 폴링·활성 배치·외부 계약 검증 보강); GitHub 작업 이슈 `#6`

### 관측성과 API 계약 검증 증거 (GitHub #7)

1. 구현: [관측성 공통 패키지](../src/main/java/com/example/coffeeordersystem/common/observability), [GlobalExceptionHandler.java](../src/main/java/com/example/coffeeordersystem/common/error/GlobalExceptionHandler.java), [OutboxMetrics.java](../src/main/java/com/example/coffeeordersystem/outbox/infrastructure/OutboxMetrics.java), [OutboxDeliveryFacade.java](../src/main/java/com/example/coffeeordersystem/outbox/application/OutboxDeliveryFacade.java), [application.yaml](../src/main/resources/application.yaml)
2. 테스트: `AT-CONTRACT-001`~`003`, `QT-CONFIG-001`, `QT-OBS-001`~`002`, DB 경합의 `IT-RESILIENCE-001`, Outbox 지표의 `EXT-OUTBOX-003`~`004`·`EXT-OUTBOX-007` — [ObservabilityTest.java](../src/test/java/com/example/coffeeordersystem/common/observability/ObservabilityTest.java), [OutboxDeliveryFacadeTest.java](../src/test/java/com/example/coffeeordersystem/outbox/application/OutboxDeliveryFacadeTest.java), [point 테스트 패키지](../src/test/java/com/example/coffeeordersystem/point), [outbox 테스트 패키지](../src/test/java/com/example/coffeeordersystem/outbox)
3. 실행: 2026-07-16 MySQL 8.4 healthy에서 `./gradlew clean check build` 성공; 전체 88개, 성공 88개, 실패 0개, 오류 0개, 제외 0개. 요청·업무·Outbox key-value 상관 로그와 SQL·자격 증명·전체 멱등키 비노출, MVC·Hikari 기본 지표, DB lock timeout·deadlock과 승인된 Outbox counter·gauge, 고 cardinality tag 부재를 검증했다. 독립 리뷰어 2명의 must·should는 최종 0개였다. 워커를 해당 프로세스에서만 비활성화한 `./gradlew bootRun`에서 `/actuator/health`의 상세 없는 `UP`, `/actuator`·`/actuator/metrics`의 `404`, 메뉴 API의 `200`을 확인했다.
4. 재현 커밋: `f7533e1`(관측 지표·상관 로그), `aac3d33`(내부 정보 비노출), `045d7f8`(Actuator·외부 실패 로그 경계); GitHub 작업 이슈 `#7`

### 다중 인스턴스와 추적성 검증 증거 (GitHub #8)

1. 구현: [CoffeeOrderSystemApplication.java](../src/main/java/com/example/coffeeordersystem/CoffeeOrderSystemApplication.java), [point 패키지](../src/main/java/com/example/coffeeordersystem/point), [order 패키지](../src/main/java/com/example/coffeeordersystem/order), [idempotency 패키지](../src/main/java/com/example/coffeeordersystem/idempotency), [outbox 패키지](../src/main/java/com/example/coffeeordersystem/outbox)
2. 테스트: `CT-APP-001`, `CT-POINT-001`~`002`, `CT-ORDER-001`, `CT-IDEM-001`~`002`, `CT-OUTBOX-001`~`002`, `QT-TRACE-001` — [MultiInstanceApiTest.java](../src/test/java/com/example/coffeeordersystem/MultiInstanceApiTest.java), [PointConcurrencyTest.java](../src/test/java/com/example/coffeeordersystem/point/PointConcurrencyTest.java), [OrderConcurrencyTest.java](../src/test/java/com/example/coffeeordersystem/order/OrderConcurrencyTest.java), [OutboxStoreTest.java](../src/test/java/com/example/coffeeordersystem/outbox/infrastructure/OutboxStoreTest.java), [TraceabilityTest.java](../src/test/java/com/example/coffeeordersystem/quality/TraceabilityTest.java)
3. 실행: 2026-07-16 MySQL 8.4 healthy에서 `./gradlew clean check build` 성공; 전체 90개, 성공 90개, 실패 0개, 오류 0개, 제외 0개. 한 JUnit 프로세스에서 서로 다른 랜덤 포트와 DataSource를 가진 독립 Spring Context 두 개가 같은 테스트 JDBC URL과 비활성 Outbox 워커를 사용했다. 첫 인스턴스의 충전 커밋을 둘째 인스턴스가 주문에 사용하고, 양쪽의 동일 멱등키 동시 충전이 같은 전체 응답과 1회 반영으로 수렴하며 주문·Outbox·잔액을 반대 Context에서 확인했다. README·추적표 상대 링크와 앵커, PRD 요구사항 행, ADR 파일·링크, 실제 테스트 DisplayName ID의 양방향 연결도 검증했다.
4. 재현 커밋: `29f2af6`(다중 Context·추적성 검사), `b5dd6ac`·`14727b9`(참조·멱등 결과 검증 보강), `2bdbd51`(문서 변경 재검사), `2252641`·`57d90ff`(현재 상태·증거 동기화), `03112b2`(전체 문서 링크·테스트 메서드 추적 강화); GitHub 작업 이슈 `#8`

### 최종 통합 검증 증거 (GitHub #9)

1. 구현: [기능별 메인 소스](../src/main/java/com/example/coffeeordersystem), [Flyway migration](../src/main/resources/db/migration), [현재 구현 상태 문서](./prd.md), [API 명세](./api-spec.md), [ERD](./erd.md), [아키텍처](./architecture.md)
2. 테스트: 승인된 현재 범위의 전체 `UT-*`·`IT-*`·`AT-*`·`EXT-*`·`CT-*`·`QT-*` — [전체 테스트 소스](../src/test/java/com/example/coffeeordersystem), [TraceabilityTest.java](../src/test/java/com/example/coffeeordersystem/quality/TraceabilityTest.java), [OrderFacadeTest.java](../src/test/java/com/example/coffeeordersystem/order/application/OrderFacadeTest.java), [PointFacadeTest.java](../src/test/java/com/example/coffeeordersystem/point/application/PointFacadeTest.java)
3. 실행: 2026-07-16 MySQL 8.4 healthy에서 `docker compose config -q`, `./gradlew clean check build` 성공; 전체 92개, 성공 92개, 실패 0개, 오류 0개, 제외 0개. `./gradlew bootRun`에서 health·메뉴·인기 메뉴 `200`, 비공개 Actuator 경로 `404`, 정의되지 않은 API 경로·메서드·Accept의 `404`·`405`·`406`을 확인했다. 실제 테스트는 문서·ADR 링크와 앵커, 요구사항·정책 상태, 테스트 ID, Flyway 스키마, API·동시성·Outbox 경계를 함께 검증한다.
4. 재현 커밋: `9ba79f9`(최종 구현 상태·추적성 동기화), `167766b`(HTTP 경계·실패 봉투·락 이후 주문 시각 보강), `6b1b00e`(락 이후 포인트 변경 시각 보강); GitHub 작업 이슈 `#9`

### Outbox 설정 경계 검증 증거 (GitHub #12)

1. 구현: [OutboxHttpSender.java](../src/main/java/com/example/coffeeordersystem/outbox/infrastructure/OutboxHttpSender.java), [OutboxWorkerConfiguration.java](../src/main/java/com/example/coffeeordersystem/outbox/infrastructure/OutboxWorkerConfiguration.java), [OutboxWorker.java](../src/main/java/com/example/coffeeordersystem/outbox/infrastructure/OutboxWorker.java)
2. 테스트: base path·trailing slash 전송의 `EXT-OUTBOX-001`, URL·포트의 `QT-CONFIG-001`, 공개 환경 변수와 폴링 범위의 `QT-CONFIG-004` — [OutboxHttpSenderTest.java](../src/test/java/com/example/coffeeordersystem/outbox/infrastructure/OutboxHttpSenderTest.java), [OutboxWorkerConfigurationTest.java](../src/test/java/com/example/coffeeordersystem/outbox/infrastructure/OutboxWorkerConfigurationTest.java)
3. 실행: 2026-07-16 MySQL 8.4 healthy에서 `./gradlew clean check build` 성공; 전체 94개, 성공 94개, 실패 0개, 오류 0개, 제외 0개. `./gradlew bootRun` 시작 성공, `/actuator/health`의 `UP`과 메뉴 API `200`을 확인했다.
4. 재현 커밋: `da28f56`(base path·포트·폴링 설정 경계), `83bda93`(빈 포트·공개 환경 변수 검증); GitHub 작업 이슈 `#12`

### Outbox 워커 복구와 배치 선점 검증 증거 (GitHub #13)

1. 구현: [OutboxWorker.java](../src/main/java/com/example/coffeeordersystem/outbox/infrastructure/OutboxWorker.java), [OutboxDeliveryFacade.java](../src/main/java/com/example/coffeeordersystem/outbox/application/OutboxDeliveryFacade.java), [OutboxStore.java](../src/main/java/com/example/coffeeordersystem/outbox/infrastructure/OutboxStore.java), [Flyway V3](../src/main/resources/db/migration/V3__optimize_outbox_claim.sql)
2. 테스트: 동기 전송 시작 예외 뒤 재폴링의 `UT-OUTBOX-004`, 50건의 잠금 조회 1회·set-based update 1회·JDBC batch 실행 0회와 고유 토큰을 검증하는 `IT-OUTBOX-004`, 다중 선점·lease·fencing의 `IT-OUTBOX-001`~`002`·`CT-OUTBOX-001`~`002`, 최초 요청의 `EXT-OUTBOX-008` — [OutboxDeliveryFacadeTest.java](../src/test/java/com/example/coffeeordersystem/outbox/application/OutboxDeliveryFacadeTest.java), [OutboxStoreTest.java](../src/test/java/com/example/coffeeordersystem/outbox/infrastructure/OutboxStoreTest.java), [OutboxWorkerIntegrationTest.java](../src/test/java/com/example/coffeeordersystem/outbox/infrastructure/OutboxWorkerIntegrationTest.java)
3. 실행: 수정 전 재현에서 `UT-OUTBOX-004`는 동기 예외가 poll 밖으로 전파됐고, `IT-OUTBOX-004`는 대표 50건에 기대 2회 대비 실제 prepared statement 101회로 실패했다. JDBC batch의 드라이버 내부 직렬 실행 리뷰 뒤에는 set-based 기대 테스트가 기존 `executeBatch` 경로에서 실패함을 추가 확인했다. 수정 후 2026-07-16 MySQL 8.4 healthy에서 `docker compose config -q`, `./gradlew clean check build` 성공; 전체 96개, 성공 96개, 실패 0개, 오류 0개, 제외 0개. 실제 MySQL 실행 계획에서 `idx_outbox_claim_order` range와 `Using index condition`을 확인했다. `./gradlew bootRun` 시작과 Flyway V3 적용, `/actuator/health`의 `UP`, 메뉴 API `200`을 확인했다. 정확성 및 테스트·문서 독립 리뷰의 must·should는 최종 0개였다.
4. 재현 커밋: `7c1e4e7`(워커 복구·배치 선점), `6c5ce09`(SQL 실행·고유 토큰 계측 보강), `fdded9a`(단일 set-based 갱신·실패 로그 분리); GitHub 작업 이슈 `#13`

### HTTP 오류 응답 검증 증거 (GitHub #14)

1. 구현: [ErrorCode.java](../src/main/java/com/example/coffeeordersystem/common/error/ErrorCode.java), [GlobalExceptionHandler.java](../src/main/java/com/example/coffeeordersystem/common/error/GlobalExceptionHandler.java), [API 명세](./api-spec.md)
2. 테스트: 공개 오류 코드 매핑의 `UT-API-001`, 404·405 JSON 봉투와 `Allow` 헤더 및 406 무본문의 `AT-CONTRACT-004` — [GlobalExceptionHandlerTest.java](../src/test/java/com/example/coffeeordersystem/common/error/GlobalExceptionHandlerTest.java), [HttpErrorContractTest.java](../src/test/java/com/example/coffeeordersystem/common/error/HttpErrorContractTest.java)
3. 실행: 수정 전 `AT-CONTRACT-004`는 정의되지 않은 경로의 빈 404 본문에서 실패했다. 수정 후 2026-07-16 MySQL 8.4 healthy에서 `./gradlew clean check build` 성공; 전체 97개, 성공 97개, 실패 0개, 오류 0개, 제외 0개. `./gradlew bootRun` 뒤 실제 HTTP로 404 `RESOURCE_NOT_FOUND` JSON, 405 `METHOD_NOT_ALLOWED` JSON과 `Allow: GET`, 406의 `Content-Length: 0`·무본문을 확인했다. 독립 리뷰의 must·should는 최종 0개였다.
4. 재현 커밋: `756e794`; GitHub 작업 이슈 `#14`

### 주문 시각 정밀도 검증 증거 (GitHub #15)

1. 구현: [OrderFacade.java](../src/main/java/com/example/coffeeordersystem/order/application/OrderFacade.java), [주문 트랜잭션](./architecture.md#11-주문-트랜잭션), [시간 API 계약](./api-spec.md#2-공통-규칙)
2. 테스트: nanosecond 입력을 microsecond로 정규화해 포인트·API·주문·Outbox·멱등 저장값을 비교하는 `IT-TIME-002`, 사용자 락 뒤 Clock 단일 호출을 검증하는 `CT-ORDER-001`, 주문 원자성·응답 계약의 `IT-ORDER-001`·`AT-CONTRACT-002` — [OrderApiTest.java](../src/test/java/com/example/coffeeordersystem/order/OrderApiTest.java), [OrderFacadeTest.java](../src/test/java/com/example/coffeeordersystem/order/application/OrderFacadeTest.java)
3. 실행: 수정 전 nanosecond 고정 Clock에서 API 시각과 MySQL `DATETIME(6)` 저장값 불일치를 재현했다. 수정 후 2026-07-16 MySQL 8.4 healthy에서 `./gradlew clean check build` 성공; 전체 97개, 성공 97개, 실패 0개, 오류 0개, 제외 0개. `./gradlew bootRun` 뒤 `/actuator/health`의 `UP`과 메뉴 API `200`을 확인했다. 독립 리뷰에서 포인트·멱등 메타데이터 검증과 전용 증거 연결을 보강한 뒤 must·should는 최종 0개였다.
4. 재현 커밋: `4a2d99a`; GitHub 작업 이슈 `#15`

### Fresh MySQL CI 검증 증거 (GitHub #16)

1. 구현: [CI workflow](../.github/workflows/ci.yml), [테스트 프로필](../src/test/resources/application-test.yaml), [테스트 환경 계약](./test-strategy.md#41-환경-계약)
2. 테스트: 빈 DB의 전체 Flyway·스키마·기준 데이터인 `IT-DB-001`, 로컬 테스트 DB 격리의 `QT-CONFIG-003`, CI 트리거·MySQL 연결·최소 권한·비밀 미참조·전체 게이트의 `QT-CONFIG-005`, migration 원문과 ERD의 `QT-SCHEMA-001` — [DatabaseMigrationTest.java](../src/test/java/com/example/coffeeordersystem/database/DatabaseMigrationTest.java), [BuildConfigurationTest.java](../src/test/java/com/example/coffeeordersystem/quality/BuildConfigurationTest.java)
3. 실행: workflow 부재 재현에서 `QT-CONFIG-005`가 `NoSuchFileException`으로 실패했다. 일회성 로컬 MySQL 8.4의 빈 `coffee_order_system_test`에서 V1·V2·V3 성공 이력과 `./gradlew clean check build` 전체 98개 성공, 실패·오류·제외 0개를 확인했다. 첫 fresh 실행에서 드러난 MySQL 세션 `SYSTEM` 표기는 CI JDBC URL에 `time_zone='+00:00'`을 명시해 해결했다. 원격 [GitHub Actions 실행 29488277992](https://github.com/0younge/coffee-order-system/actions/runs/29488277992)은 `main` SHA `105be34978fb307c60895a4ab575cfe6eea75278`에서 MySQL 8.4 초기화와 전체 게이트를 1분 46초에 성공했고 annotation은 0개였다. 독립 리뷰의 must·should·nit은 최종 0개였다.
4. 재현 커밋: `dccd6cf`(fresh CI·전체 migration 검증), `105be34`(Node 24 기반 공식 action으로 갱신); GitHub 작업 이슈 `#16`

### 문서와 하네스 동기화 검증 증거 (GitHub #17)

1. 구현: [AGENTS.md](../AGENTS.md), [도메인 컨텍스트](./context.md#트랜잭션-경계), [아키텍처 결정 근거](./architecture.md#3-결정-근거), [ADR-0025](./adr/0025-lock-user-before-idempotency-record.md)
2. 테스트: 하네스 길이·포인터·현재 상태와 포인트·주문의 사용자 락 선행 순서·ADR 링크를 검사하는 `QT-HARNESS-001`, 문서 링크·테스트 ID의 `QT-TRACE-001`, 실제 잠금 순서의 `CT-POINT-001`·`CT-ORDER-001` — [HarnessConfigurationTest.java](../src/test/java/com/example/coffeeordersystem/quality/HarnessConfigurationTest.java), [TraceabilityTest.java](../src/test/java/com/example/coffeeordersystem/quality/TraceabilityTest.java), [PointFacadeTest.java](../src/test/java/com/example/coffeeordersystem/point/application/PointFacadeTest.java), [OrderFacadeTest.java](../src/test/java/com/example/coffeeordersystem/order/application/OrderFacadeTest.java)
3. 실행: 수정 전 `QT-HARNESS-001`은 `AGENTS.md`의 “Flyway·업무 기능 구현 전” 문구에서 실패했고, context의 기존 멱등 선행 순서와 architecture의 구현 전 문구도 회귀 검사에서 실패했다. 수정 후 `AGENTS.md` 90줄과 `CLAUDE.md` 포인터를 확인하고 2026-07-16 MySQL 8.4 healthy에서 `./gradlew clean check build` 성공; 전체 99개, 성공 99개, 실패 0개, 오류 0개, 제외 0개. 독립 문서·정확성 리뷰에서 구현 상태·링크 assertion과 전용 증거를 보강한 뒤 must·should·nit은 최종 0개였다.
4. 재현 커밋: `d46ec53`; GitHub 작업 이슈 `#17`

### 최종 전체 리뷰 검증 증거 (GitHub #18)

1. 구현: JSON 응답 협상을 핸들러 실행 전에 확정하는 [메뉴](../src/main/java/com/example/coffeeordersystem/menu/api/MenuController.java)·[포인트](../src/main/java/com/example/coffeeordersystem/point/api/PointController.java)·[주문](../src/main/java/com/example/coffeeordersystem/order/api/OrderController.java) 매핑, DB 시간 정밀도의 [인기 메뉴 구간](../src/main/java/com/example/coffeeordersystem/menu/domain/PopularMenuWindow.java), 엄격한 [Outbox 워커 설정](../src/main/java/com/example/coffeeordersystem/outbox/infrastructure/OutboxWorkerConfiguration.java), [Claude autodev 포인터](../.claude/skills/autodev), 하네스 변경도 테스트를 다시 실행하게 하는 [Gradle 외부 입력](../build.gradle)
2. 테스트: 충전·주문의 `406` 선거절과 업무·멱등·주문·Outbox 불변을 검증하는 `AT-CONTRACT-004`, nanosecond 경계를 microsecond 반개구간으로 검증하는 `UT-POPULAR-001`·`IT-POPULAR-001`, 워커 활성값과 전용 Context 격리의 `QT-CONFIG-001`, 하네스·문서 현재 상태와 Gradle 입력의 `QT-HARNESS-001`·`QT-TRACE-001`·`QT-FORMAT-001` — [PointApiTest.java](../src/test/java/com/example/coffeeordersystem/point/PointApiTest.java), [OrderApiTest.java](../src/test/java/com/example/coffeeordersystem/order/OrderApiTest.java), [PopularMenuWindowTest.java](../src/test/java/com/example/coffeeordersystem/menu/domain/PopularMenuWindowTest.java), [PopularMenuApiTest.java](../src/test/java/com/example/coffeeordersystem/menu/PopularMenuApiTest.java), [OutboxWorkerConfigurationTest.java](../src/test/java/com/example/coffeeordersystem/outbox/infrastructure/OutboxWorkerConfigurationTest.java), [OutboxWorkerIntegrationTest.java](../src/test/java/com/example/coffeeordersystem/outbox/infrastructure/OutboxWorkerIntegrationTest.java), [HarnessConfigurationTest.java](../src/test/java/com/example/coffeeordersystem/quality/HarnessConfigurationTest.java), [TraceabilityTest.java](../src/test/java/com/example/coffeeordersystem/quality/TraceabilityTest.java), [BuildConfigurationTest.java](../src/test/java/com/example/coffeeordersystem/quality/BuildConfigurationTest.java)
3. 실행: 수정 전 실제 HTTP에서 충전 요청은 빈 `406`을 반환하면서 잔액과 멱등 결과를 커밋했고 주문도 같은 구조임을 테스트로 재현했다. nanosecond 인기 메뉴 경계, 잘못된 워커 활성값도 수정 전 테스트가 실패했다. 수정 후 2026-07-16 MySQL 8.4 healthy에서 `docker compose config -q`, `./gradlew clean check build` 성공; 전체 103개, 성공 103개, 실패 0개, 오류 0개, 제외 0개. `./gradlew bootRun`에서 health·메뉴·인기 메뉴 `200`, 정의되지 않은 경로 `404`, 미지원 메서드 `405`, GET·충전·주문의 JSON 거절 `406` 무본문을 확인했으며, 두 변경 요청 전후의 잔액·멱등·주문·Outbox 상태가 모두 동일했다. 런타임 정확성 및 문서·계약 독립 리뷰의 must·should·nit은 최종 0개였다.
4. 재현 커밋: `765cc2f`, `2dad20c`; GitHub 작업 이슈 `#18`

## 3. 기능 요구사항 추적

| 요구사항 | 요구 내용 | 설계 | API 계약 | 관련 ADR | 계획된 테스트 ID | 구현 상태 | 현재 증거 |
|---|---|---|---|---|---|---|---|
| [FR-01](./prd.md#fr-01-메뉴-목록-조회) | 메뉴 ID·이름·가격, ID 오름차순, Flyway 초기 메뉴 | [모듈러 모놀리스](./architecture.md#6-모듈러-모놀리스), [`menus`](./erd.md#42-menus) | [메뉴 목록 조회](./api-spec.md#4-메뉴-목록-조회) | [0006](./adr/0006-use-feature-oriented-modular-monolith.md), [0010](./adr/0010-test-against-mysql-with-docker-compose.md) | `IT-DB-001`, `AT-MENU-001` | 검증됨 | Flyway 메뉴는 [#1 증거](#기반선-검증-증거-github-1), 현재 값·ID 오름차순·무상태 JSON 응답은 [#2 증거](#메뉴-api-검증-증거-github-2)로 검증 |
| [FR-02](./prd.md#fr-02-포인트-충전) | 요청 본문의 기존 사용자 ID, 과제·로컬 기준 사용자, 양의 정수 충전, 덧셈 overflow 방지, UUID 멱등키 | [사용자 식별 경계](./architecture.md#8-사용자-식별-경계), [포인트 충전](./architecture.md#10-포인트-충전), [`users`](./erd.md#41-users), [`idempotency_records`](./erd.md#44-idempotency_records) | [포인트 충전](./api-spec.md#5-포인트-충전) | [0001](./adr/0001-use-database-pessimistic-locking-for-points.md), [0011](./adr/0011-return-temporary-unavailable-on-database-contention.md), [0015](./adr/0015-protect-mutations-with-idempotency-keys.md), [0016](./adr/0016-store-positive-point-balance-without-arbitrary-cap.md), [0020](./adr/0020-enforce-lifecycle-invariants-with-database-checks.md), [0022](./adr/0022-accept-user-id-without-authentication.md), [0024](./adr/0024-seed-reference-user-for-local-execution.md), [0025](./adr/0025-lock-user-before-idempotency-record.md) | `UT-POINT-001`, `UT-POINT-002`, `UT-IDEM-001`, `UT-IDEM-002`, `UT-IDEM-003`, `IT-DB-001`, `IT-POINT-001`, `IT-POINT-002`, `IT-IDEM-001`, `IT-IDEM-002`, `IT-RESILIENCE-001`, `AT-USER-001`, `AT-POINT-001`, `AT-POINT-002`, `AT-POINT-003`, `AT-POINT-004`, `CT-POINT-001`, `CT-POINT-002`, `CT-IDEM-001`, `CT-IDEM-002` | 검증됨 | 스키마·기준 사용자는 [#1 증거](#기반선-검증-증거-github-1), 충전·멱등 업무와 동시성·롤백은 [#3 증거](#포인트-충전-검증-증거-github-3), JSON 거절 선처리와 상태 불변은 [#18 증거](#최종-전체-리뷰-검증-증거-github-18)로 검증 |
| [FR-03](./prd.md#fr-03-주문-및-결제) | 기존 사용자 ID, 단일 메뉴, 주문 시점 스냅샷, 차감·주문·Outbox 원자성, 멱등키 | [주문 트랜잭션](./architecture.md#11-주문-트랜잭션), [`orders`](./erd.md#43-orders) | [주문 및 결제](./api-spec.md#6-주문-및-결제) | [0001](./adr/0001-use-database-pessimistic-locking-for-points.md), [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md), [0009](./adr/0009-model-single-menu-orders-with-snapshots.md), [0011](./adr/0011-return-temporary-unavailable-on-database-contention.md), [0015](./adr/0015-protect-mutations-with-idempotency-keys.md), [0016](./adr/0016-store-positive-point-balance-without-arbitrary-cap.md), [0020](./adr/0020-enforce-lifecycle-invariants-with-database-checks.md), [0022](./adr/0022-accept-user-id-without-authentication.md), [0025](./adr/0025-lock-user-before-idempotency-record.md) | `UT-POINT-003`, `UT-POINT-004`, `UT-ORDER-001`, `UT-ORDER-002`, `UT-IDEM-001`, `UT-IDEM-002`, `UT-IDEM-003`, `IT-ORDER-001`, `IT-ORDER-002`, `IT-ORDER-003`, `IT-TIME-002`, `IT-RESILIENCE-001`, `AT-USER-001`, `AT-ORDER-001`, `AT-ORDER-002`, `AT-ORDER-003`, `AT-ORDER-004`, `AT-ORDER-005`, `CT-ORDER-001`, `CT-IDEM-001`, `CT-IDEM-002` | 검증됨 | 스키마·제약은 [#1 증거](#기반선-검증-증거-github-1), 주문 트랜잭션·API·원자성·동시성은 [#4 증거](#주문결제-검증-증거-github-4), 교차 저장 정밀도는 [#15 증거](#주문-시각-정밀도-검증-증거-github-15)로 검증하고, JSON 거절 선처리와 상태 불변은 [#18 증거](#최종-전체-리뷰-검증-증거-github-18)로 검증 |
| [FR-04](./prd.md#fr-04-주문-데이터-전송) | 주문 응답과 외부 장애 분리, 정상·backlog 없음 조건의 커밋 후 2초 이내 최초 HTTP 요청, 현재 claim의 실패 횟수와 상태별 필드 정규화 | [Outbox 전달](./architecture.md#12-outbox-전달), [`outbox_events`](./erd.md#45-outbox_events) | [외부 데이터 수집 API](./api-spec.md#10-외부-데이터-수집-api-계약) | [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md), [0014](./adr/0014-send-outbox-batches-asynchronously.md), [0017](./adr/0017-bound-first-outbox-attempt-latency.md), [0020](./adr/0020-enforce-lifecycle-invariants-with-database-checks.md), [0023](./adr/0023-define-outbox-field-lifecycle.md) | `UT-OUTBOX-001`~`004`, `IT-OUTBOX-001`, `IT-OUTBOX-002`, `IT-OUTBOX-004`, `EXT-OUTBOX-001`~`009`, `CT-OUTBOX-001`, `CT-OUTBOX-002`, `QT-CONFIG-001`, `QT-CONFIG-004` | 검증됨 | 주문·`PENDING` 이벤트 원자 저장은 [#4 증거](#주문결제-검증-증거-github-4), 선점·병렬 HTTP·재시도·lease·fencing·상태 정규화는 [#6 증거](#outbox-전달-검증-증거-github-6), base path·포트·폴링 설정 경계는 [#12 증거](#outbox-설정-경계-검증-증거-github-12), 동기 시작 예외 복구와 50건 set-based 선점은 [#13 증거](#outbox-워커-복구와-배치-선점-검증-증거-github-13)로 검증하고, 워커 활성값 검증은 [#18 증거](#최종-전체-리뷰-검증-증거-github-18)로 검증 |
| [FR-05](./prd.md#fr-05-인기-메뉴-조회) | 직전 7×24시간 `PAID` 주문, 상위 3개, 동률 ID 오름차순, 부족 시 존재 항목만 | [인기 메뉴 조회](./architecture.md#13-인기-메뉴-조회), [`orders`](./erd.md#43-orders) | [인기 메뉴 조회](./api-spec.md#7-인기-메뉴-조회) | [0004](./adr/0004-calculate-popular-menus-from-paid-orders.md) | `UT-POPULAR-001`, `UT-POPULAR-002`, `IT-POPULAR-001`, `IT-POPULAR-002`, `IT-POPULAR-003`, `AT-POPULAR-001`, `AT-POPULAR-002`; 후속 `PT-POPULAR-001` | 검증됨 | 집계 인덱스는 [#1 증거](#기반선-검증-증거-github-1), 7일 경계·정렬·현재 메뉴 결합·무상태 API는 [#5 증거](#인기-메뉴-검증-증거-github-5)로 검증하고, nanosecond 조회 경계는 [#18 증거](#최종-전체-리뷰-검증-증거-github-18)로 검증 |

## 4. 비기능 요구사항 추적

| 요구사항 | 요구 내용 | 설계 | API 영향 | 관련 ADR | 계획된 테스트 ID | 구현 상태 | 현재 증거 |
|---|---|---|---|---|---|---|---|
| [NFR-01](./prd.md#nfr-01-데이터-일관성) | 공용 MySQL 정본, DB 제약, 실제 MySQL 검증, 외부 호출의 고객 트랜잭션 분리 | [데이터·트랜잭션 경계](./architecture.md#7-요청별-데이터트랜잭션-경계), [주요 불변식](./erd.md#9-트랜잭션-불변식) | 충전·주문·외부 전달 | [0001](./adr/0001-use-database-pessimistic-locking-for-points.md), [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md), [0005](./adr/0005-establish-java-spring-mysql-platform-baseline.md), [0010](./adr/0010-test-against-mysql-with-docker-compose.md), [0011](./adr/0011-return-temporary-unavailable-on-database-contention.md), [0015](./adr/0015-protect-mutations-with-idempotency-keys.md), [0016](./adr/0016-store-positive-point-balance-without-arbitrary-cap.md), [0018](./adr/0018-isolate-test-database-and-outbox-workers.md), [0020](./adr/0020-enforce-lifecycle-invariants-with-database-checks.md), [0025](./adr/0025-lock-user-before-idempotency-record.md) | `IT-DB-001`~`004`, `IT-POINT-001`~`002`, `IT-ORDER-001`, `IT-ORDER-003`, `IT-OUTBOX-001`, `IT-OUTBOX-004`, `IT-RESILIENCE-001`, `QT-SCHEMA-001`, `QT-CONFIG-003`, `QT-CONFIG-005`, `CT-APP-001` | 검증됨 | DB·제약·업무 트랜잭션은 [#1](#기반선-검증-증거-github-1)·[#3](#포인트-충전-검증-증거-github-3)·[#4](#주문결제-검증-증거-github-4), 외부 호출 분리와 MySQL 선점은 [#6](#outbox-전달-검증-증거-github-6)·[#13 증거](#outbox-워커-복구와-배치-선점-검증-증거-github-13), 독립 Context의 공용 DB 정합성은 [#8 증거](#다중-인스턴스와-추적성-검증-증거-github-8), 빈 DB 전체 migration은 [#16 증거](#fresh-mysql-ci-검증-증거-github-16)로 검증 |
| [NFR-02](./prd.md#nfr-02-성능과-용량-측정) | Outbox 정상 최초 요청 2초는 첫 완료 게이트에서 검증하고 그 밖의 TBD 성능 기준선은 기능 완료 후 별도 측정 | [성능·확장성 검증](./architecture.md#15-성능확장성-검증) | 전체 API와 Outbox | [0017](./adr/0017-bound-first-outbox-attempt-latency.md) | 필수 `EXT-OUTBOX-008`, `QT-CONFIG-004`; 후속 `PT-API-001`, `PT-LOCK-001`, `PT-POPULAR-001`, `PT-OUTBOX-001` | 검증됨 | 기본 1초 폴링의 2초 이내 최초 요청은 [#6 증거](#outbox-전달-검증-증거-github-6), 이를 보존하는 설정 범위는 [#12 증거](#outbox-설정-경계-검증-증거-github-12)로 검증; 합의되지 않은 `PT-*` 기준선은 현재 범위 제외 |
| [NFR-03](./prd.md#nfr-03-확장성과-다중-인스턴스) | 로컬 상태·락 미사용, 공용 DB 기반 수평 확장, Outbox 분산 선점 | [런타임 구조](./architecture.md#5-런타임-구조), [점유와 fencing](./architecture.md#121-점유와-fencing) | 전체 API와 외부 이벤트 | [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md), [0006](./adr/0006-use-feature-oriented-modular-monolith.md), [0014](./adr/0014-send-outbox-batches-asynchronously.md), [0018](./adr/0018-isolate-test-database-and-outbox-workers.md), [0022](./adr/0022-accept-user-id-without-authentication.md), [0025](./adr/0025-lock-user-before-idempotency-record.md) | `CT-APP-001`, `CT-POINT-001`, `CT-POINT-002`, `IT-OUTBOX-001`, `IT-OUTBOX-004`, `CT-OUTBOX-001`, `CT-OUTBOX-002`; 후속 `PT-LOCK-001`, `PT-OUTBOX-001` | 검증됨 | 공용 MySQL 업무 동시성은 [#3](#포인트-충전-검증-증거-github-3)·[#4](#주문결제-검증-증거-github-4), Outbox 분산 선점·fencing은 [#6](#outbox-전달-검증-증거-github-6)·[#13 증거](#outbox-워커-복구와-배치-선점-검증-증거-github-13), 독립 Spring Context의 충전·주문·멱등 교차 처리는 [#8 증거](#다중-인스턴스와-추적성-검증-증거-github-8)로 검증 |
| [NFR-04](./prd.md#nfr-04-입력과-비밀정보-보호) | 엄격한 입력 검증, 결정적인 오류 우선순위, 환경 변수 비밀 주입, 내부 오류·자격 증명 미노출, 사용자 소유권 미검증 명시 | [사용자 식별 경계](./architecture.md#8-사용자-식별-경계), [관측성과 보안 운영](./architecture.md#17-관측성과-보안-운영) | 충전·주문과 공통 오류 | [0005](./adr/0005-establish-java-spring-mysql-platform-baseline.md), [0022](./adr/0022-accept-user-id-without-authentication.md) | `UT-API-001`, `AT-USER-001`, `AT-CONTRACT-001`, `AT-CONTRACT-003`, `AT-CONTRACT-004`, `QT-CONFIG-001`, `QT-OBS-001` | 검증됨 | 환경 변수·HTTP 오류 경계와 업무 입력은 [#1](#기반선-검증-증거-github-1)~[#4](#주문결제-검증-증거-github-4), 외부 주소 경계는 [#6 증거](#outbox-전달-검증-증거-github-6)·[#12 증거](#outbox-설정-경계-검증-증거-github-12), 라우팅 404·405 JSON과 협상 406 예외는 [#14 증거](#http-오류-응답-검증-증거-github-14), 그 밖의 로그·오류 우선순위는 [#7](#관측성과-api-계약-검증-증거-github-7)·[#9 증거](#최종-통합-검증-증거-github-9)로 검증하고, 변경 요청의 406 선거절과 엄격한 워커 활성값은 [#18 증거](#최종-전체-리뷰-검증-증거-github-18)로 검증 |
| [NFR-05](./prd.md#nfr-05-장애-복구와-전달-신뢰성) | 주문과 외부 장애 분리, 정상 조건 2초 이내 최초 HTTP 요청, at-least-once, lease 복구, 결정적인 `PUBLISHED`·`FAILED` 필드 | [Outbox 전달](./architecture.md#12-outbox-전달), [장애 처리](./architecture.md#16-장애-처리) | 주문·외부 이벤트 | [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md), [0014](./adr/0014-send-outbox-batches-asynchronously.md), [0017](./adr/0017-bound-first-outbox-attempt-latency.md), [0023](./adr/0023-define-outbox-field-lifecycle.md) | `IT-ORDER-001`, `IT-ORDER-003`, `UT-OUTBOX-004`, `IT-OUTBOX-002`, `IT-OUTBOX-004`, `EXT-OUTBOX-002`, `EXT-OUTBOX-003`, `EXT-OUTBOX-004`, `EXT-OUTBOX-005`, `EXT-OUTBOX-006`, `EXT-OUTBOX-007`, `EXT-OUTBOX-008`, `EXT-OUTBOX-009` | 검증됨 | 주문·이벤트 원자 저장은 [#4 증거](#주문결제-검증-증거-github-4), 선점·HTTP 전달·재시도·lease 복구·필드 정규화는 [#6 증거](#outbox-전달-검증-증거-github-6), 동기 전송 시작 실패 뒤 워커 복구는 [#13 증거](#outbox-워커-복구와-배치-선점-검증-증거-github-13)로 검증 |
| [NFR-06](./prd.md#nfr-06-관찰-가능성) | key-value 상관 로그, MVC·Hikari 기본 지표, DB 경합·Outbox 최소 counter·gauge, health만 공개 | [관측성과 보안 운영](./architecture.md#17-관측성과-보안-운영) | 전체 API와 워커 | [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md), [0013](./adr/0013-use-actuator-and-micrometer-for-observability.md) | `QT-CONFIG-001`, `QT-OBS-001`, `QT-OBS-002`, `EXT-OUTBOX-003`, `EXT-OUTBOX-004`; 후속 `PT-API-001`, `PT-LOCK-001`, `PT-OUTBOX-001` | 검증됨 | 요청·업무·외부 호출의 key-value 상관 로그, MVC·Hikari와 DB 경합·Outbox 지표, 상세 없는 health 단독 공개를 [#7 증거](#관측성과-api-계약-검증-증거-github-7)로 검증 |
| [NFR-07](./prd.md#nfr-07-시간-결정성) | 주입 가능한 Clock, 요청당 단일 기준 시각, DB·애플리케이션·테스트 UTC | [인기 메뉴 조회](./architecture.md#13-인기-메뉴-조회), [관측성과 보안 운영](./architecture.md#17-관측성과-보안-운영) | 주문·인기 메뉴·외부 이벤트 | [0004](./adr/0004-calculate-popular-menus-from-paid-orders.md), [0005](./adr/0005-establish-java-spring-mysql-platform-baseline.md) | `UT-OUTBOX-001`, `UT-POPULAR-001`, `IT-POPULAR-001`, `IT-OUTBOX-002`, `IT-TIME-001`, `IT-TIME-002`, `AT-CONTRACT-002`, `EXT-OUTBOX-001`, `QT-CONFIG-002`, `CT-POINT-001`, `CT-ORDER-001` | 검증됨 | UTC 기반선·주문·인기 메뉴 시각은 [#1](#기반선-검증-증거-github-1)·[#4](#주문결제-검증-증거-github-4)·[#5](#인기-메뉴-검증-증거-github-5), Outbox lease·재시도·발생 시각은 [#6](#outbox-전달-검증-증거-github-6), 사용자 락 이후 포인트·주문 시각 확정과 microsecond 정규화는 [#9](#최종-통합-검증-증거-github-9)·[#15 증거](#주문-시각-정밀도-검증-증거-github-15)로 검증하고, 인기 메뉴의 DB microsecond 경계 정규화는 [#18 증거](#최종-전체-리뷰-검증-증거-github-18)로 검증 |

## 5. 세부 정책 추적

다음 항목은 기존 요구사항을 구현 가능한 수준으로 구체화한 정책이다. 승인 여부는 관련 ADR 상태를 따른다. Outbox 최초 요청 2초 기준은 “실시간”을 검증 가능하게 구체화한 승인 목표이며, 그 밖의 정량 성능 목표는 아직 추가하지 않는다.

| 정책 ID | 확정 내용 | 반영 문서·API | 관련 ADR | 계획된 테스트 ID | 구현 상태 | 현재 증거 |
|---|---|---|---|---|---|---|
| `POL-PLATFORM-01` | Java 17, Spring Boot 4.1.0, Gradle 9.5.1, MySQL 8.4 LTS | [README](../README.md), [아키텍처](./architecture.md) | [0005](./adr/0005-establish-java-spring-mysql-platform-baseline.md) | `IT-DB-001`, `QT-CONFIG-002` | 검증됨 | 문서·빌드·Compose·실제 MySQL Flyway 실행 일치를 [#1 증거](#기반선-검증-증거-github-1)로 검증 |
| `POL-ARCH-01` | 기능 우선 API·Application·Domain·Infrastructure 책임, Controller→Facade, 다른 기능은 공개 Application 계약만 참조하고 순환·빈 위임 계층 금지 | [모듈러 모놀리스](./architecture.md#6-모듈러-모놀리스), [기능 경계](./context.md#기능-경계와-의존-방향) | [0006](./adr/0006-use-feature-oriented-modular-monolith.md), [0026](./adr/0026-refine-feature-first-layered-architecture.md) | `QT-ARCH-001`, 전체 `AT-*`·`IT-*`·`CT-*` 회귀 | 구현 중 | 승인 구조와 변경 전 103개 회귀 기준은 [#20 증거](#구조-리팩토링-기준선-증거-github-20)로 고정; 패키지·Facade·구조 테스트 구현과 최종 증거는 추적 이슈 `#19`에서 진행 중 |
| `POL-LOMBOK-01` | Lombok은 `compileOnly`·`annotationProcessor`의 유일한 신규 의존성으로 사용하고 생성자·로거·보호된 JPA 기본 생성자·선택 Getter에 제한하며 위험한 Entity annotation 금지 | [승인된 기술 기준선](./architecture.md#2-승인된-기술-기준선), [테스트 환경](./test-strategy.md#41-환경-계약) | [0027](./adr/0027-use-lombok-with-restrictions.md) | `QT-DEPS-001`, `QT-LOMBOK-001` | 구현 중 | 정책 승인과 검증 계획은 ADR·문서에 반영; Gradle·소스·annotation processing 실행 증거는 작업 이슈 `#22`~`#25`에서 추가 예정 |
| `POL-IDEM-01` | 사용자 행 잠금 뒤 MySQL upsert·잠금 조회로 키를 선점하고 업무 처리와 멱등 결과를 한 트랜잭션에 커밋 | [멱등 처리](./architecture.md#9-멱등-처리), [ERD](./erd.md#44-idempotency_records) | [0015](./adr/0015-protect-mutations-with-idempotency-keys.md), [0025](./adr/0025-lock-user-before-idempotency-record.md) | `IT-ORDER-001`, `IT-ORDER-002`, `IT-ORDER-003`, `IT-IDEM-001`, `IT-IDEM-002`, `CT-IDEM-001`, `CT-IDEM-002`, `QT-HARNESS-001` | 검증됨 | 충전의 선점·결과 재사용·키 충돌은 [#3 증거](#포인트-충전-검증-증거-github-3), 주문의 성공·실패 원자 커밋과 동시 재시도는 [#4 증거](#주문결제-검증-증거-github-4), 문서·하네스의 잠금 선행 안내는 [#17 증거](#문서와-하네스-동기화-검증-증거-github-17)로 검증 |
| `POL-IDEM-02` | 완료 멱등 레코드는 최소 24시간 보존하며 현재 정리 기능은 구현하지 않음 | [ERD](./erd.md#44-idempotency_records) | [0015](./adr/0015-protect-mutations-with-idempotency-keys.md) | 예약: `IT-IDEM-003` | 현재 범위 제외 | 정리 기능 도입 시 구현·검증 |
| `POL-POINT-01` | 양의 signed `BIGINT` 충전만 허용하고 임의 상한 없이 덧셈 overflow를 `409 POINT_BALANCE_OVERFLOW`로 거절 | [포인트 충전](./architecture.md#10-포인트-충전), [API](./api-spec.md#5-포인트-충전), [ERD](./erd.md#41-users) | [0016](./adr/0016-store-positive-point-balance-without-arbitrary-cap.md) | `UT-POINT-001`, `UT-POINT-002`, `AT-POINT-001`, `AT-POINT-002`, `CT-POINT-001` | 검증됨 | 입력 범위·정확한 잔액 증가·overflow 상태 불변과 멱등 결과를 [#3 증거](#포인트-충전-검증-증거-github-3)로 검증 |
| `POL-OUTBOX-01` | `SKIP LOCKED`와 `claim_token`으로 선점; lease 30초; 이전 토큰의 늦은 갱신 차단 | [점유와 fencing](./architecture.md#121-점유와-fencing), [ERD](./erd.md#45-outbox_events) | [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md) | `IT-OUTBOX-001`, `IT-OUTBOX-002`, `IT-OUTBOX-004`, `CT-OUTBOX-001`, `CT-OUTBOX-002`, `EXT-OUTBOX-007` | 검증됨 | 실제 MySQL 다중 선점·lease 경계·stale token 거절을 [#6](#outbox-전달-검증-증거-github-6)·[#13 증거](#outbox-워커-복구와-배치-선점-검증-증거-github-13)로 검증 |
| `POL-OUTBOX-02` | 즉시 첫 전송 후 1분·5분·30분 재시도, 현재 claim의 반영 실패마다 count 증가, 총 4회; 연결 2초·요청 전체 5초 | [재시도 일정](./architecture.md#122-재시도-일정) | [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md), [0014](./adr/0014-send-outbox-batches-asynchronously.md), [0023](./adr/0023-define-outbox-field-lifecycle.md) | `UT-OUTBOX-001`, `UT-OUTBOX-003`, `EXT-OUTBOX-002`, `EXT-OUTBOX-003`, `EXT-OUTBOX-004`, `EXT-OUTBOX-005` | 검증됨 | 재시도 일정·실패 횟수·timeout·최대 시도를 [#6 증거](#outbox-전달-검증-증거-github-6)로 검증 |
| `POL-OUTBOX-03` | `2xx` 성공; 네트워크·timeout·408·429·5xx 재시도; redirect 없는 3xx·다른 4xx 즉시 `FAILED` | [HTTP 결과 분류](./architecture.md#123-http-결과-분류) | [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md) | `UT-OUTBOX-002`, `EXT-OUTBOX-003`, `EXT-OUTBOX-004`, `EXT-OUTBOX-005` | 검증됨 | JDK Mock HTTP 응답·네트워크·timeout 분류를 [#6 증거](#outbox-전달-검증-증거-github-6)로 검증 |
| `POL-OUTBOX-04` | `PUBLISHED`는 최소 30일 보존하고 `FAILED`는 자동 삭제하지 않으며 현재 정리 기능은 구현하지 않음 | [ERD](./erd.md#45-outbox_events) | [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md) | 예약: `IT-OUTBOX-003` | 현재 범위 제외 | 정리 기능 도입 시 구현·검증 |
| `POL-OUTBOX-05` | 활성 배치가 없을 때 기본 1초마다 오래된 due 이벤트 최대 50건을 선점해 비동기 병렬 전송 | [점유와 fencing](./architecture.md#121-점유와-fencing) | [0003](./adr/0003-deliver-order-events-with-transactional-outbox.md), [0014](./adr/0014-send-outbox-batches-asynchronously.md) | `UT-OUTBOX-004`, `IT-OUTBOX-001`, `IT-OUTBOX-004`, `EXT-OUTBOX-002`, `CT-OUTBOX-001`, `QT-CONFIG-004` | 검증됨 | 기본 폴링·단일 활성 배치·병렬 요청·다중 워커 선점은 [#6](#outbox-전달-검증-증거-github-6)·[#13 증거](#outbox-워커-복구와-배치-선점-검증-증거-github-13), 폴링 설정 경계는 [#12 증거](#outbox-설정-경계-검증-증거-github-12)로 검증 |
| `POL-OUTBOX-06` | 워커 정상·전송 가능한 기존 backlog 없음 조건에서 주문 커밋 후 2초 이내 최초 HTTP 요청 시작 | [Outbox 전달](./architecture.md#12-outbox-전달), [외부 API](./api-spec.md#10-외부-데이터-수집-api-계약) | [0017](./adr/0017-bound-first-outbox-attempt-latency.md) | 필수 `EXT-OUTBOX-002`, `EXT-OUTBOX-008`, `QT-CONFIG-004`; 후속 `PT-OUTBOX-001` | 검증됨 | 기본 1초 폴링의 최초 요청 시작은 [#6 증거](#outbox-전달-검증-증거-github-6), 2초 계약을 보존하는 설정 범위는 [#12 증거](#outbox-설정-경계-검증-증거-github-12)로 검증 |
| `POL-OUTBOX-07` | 재시도 중 마지막 오류 보존, `PUBLISHED` 오류·예약 정리, `FAILED` 마지막 오류 보존과 예약 정리 | [ERD 상태 전이](./erd.md#72-outbox-이벤트), [외부 API](./api-spec.md#10-외부-데이터-수집-api-계약) | [0023](./adr/0023-define-outbox-field-lifecycle.md) | `IT-DB-002`, `EXT-OUTBOX-003`, `EXT-OUTBOX-004`, `EXT-OUTBOX-005`, `EXT-OUTBOX-009` | 검증됨 | 재시도·성공·영구 실패·최대 시도 필드 조합을 [#6 증거](#outbox-전달-검증-증거-github-6)로 검증 |
| `POL-USER-01` | 충전·주문 본문의 양의 정수 `userId`로 기존 사용자를 식별하고 과제·로컬에는 ID 1·0P를 seed하며 사용자 부재는 멱등 저장 없이 `404` | [사용자 식별 경계](./architecture.md#8-사용자-식별-경계), [API 공통 규칙](./api-spec.md#2-공통-규칙) | [0022](./adr/0022-accept-user-id-without-authentication.md), [0024](./adr/0024-seed-reference-user-for-local-execution.md) | `IT-DB-001`, `AT-USER-001`, `AT-POINT-001`, `AT-ORDER-001`, `CT-APP-001` | 검증됨 | 기준 사용자 seed는 [#1 증거](#기반선-검증-증거-github-1), 충전·주문의 사용자 입력과 부재 계약은 [#3](#포인트-충전-검증-증거-github-3)·[#4](#주문결제-검증-증거-github-4), 다중 Spring Context의 동일 사용자 상태 처리는 [#8 증거](#다중-인스턴스와-추적성-검증-증거-github-8)로 검증 |
| `POL-OBS-01` | MVC·Hikari 기본 지표, DB 경합·Outbox 최소 counter·gauge와 key-value 로그를 구현하고 HTTP에는 상세 정보를 숨긴 health만 노출 | [관측성과 보안 운영](./architecture.md#17-관측성과-보안-운영) | [0013](./adr/0013-use-actuator-and-micrometer-for-observability.md) | `QT-OBS-001`, `QT-OBS-002`, `QT-CONFIG-001` | 검증됨 | 승인된 로그·지표 목록과 무태그 사용자 지표, `/actuator/health` 단독 공개를 [#7 증거](#관측성과-api-계약-검증-증거-github-7)로 검증 |
| `POL-POPULAR-01` | 주문 수는 결제 완료 주문에서 집계하고 이름·가격은 현재 `menus`에서 반환 | [인기 메뉴 조회](./architecture.md#13-인기-메뉴-조회), [API](./api-spec.md#7-인기-메뉴-조회) | [0004](./adr/0004-calculate-popular-menus-from-paid-orders.md) | `IT-POPULAR-001`, `IT-POPULAR-002`, `IT-POPULAR-003`, `AT-POPULAR-002` | 검증됨 | `PAID` 주문 직접 집계와 현재 메뉴 이름·가격 결합을 [#5 증거](#인기-메뉴-검증-증거-github-5)로 검증 |
| `POL-PERF-01` | Outbox 정상 최초 요청 2초만 첫 완료 게이트에서 검증하고 그 밖의 `PT-*` 기준선은 기능 완료 후 별도 측정 | [테스트 전략](./test-strategy.md#56-성능-기준선-테스트) | [0017](./adr/0017-bound-first-outbox-attempt-latency.md) | 필수 `EXT-OUTBOX-008`; 후속 `PT-API-001`, `PT-LOCK-001`, `PT-POPULAR-001`, `PT-OUTBOX-001` | 검증됨 | 현재 범위의 필수 2초 계약은 [#6 증거](#outbox-전달-검증-증거-github-6)로 검증; 그 밖의 합의되지 않은 수치는 현재 범위 제외 |
| `POL-RUN-01` | Docker Compose는 MySQL만 실행하고 애플리케이션은 호스트에서 `./gradlew bootRun` | [테스트 환경](./test-strategy.md#41-환경-계약), [README](../README.md) | [0010](./adr/0010-test-against-mysql-with-docker-compose.md) | `IT-DB-001`, `QT-CONFIG-002` | 검증됨 | 2026-07-16 MySQL healthy와 호스트 `bootRun`·health `UP`을 [#1 증거](#기반선-검증-증거-github-1)로 확인 |
| `POL-TEST-01` | 멱등 Compose 스크립트로 개발·테스트 DB를 준비하고 일반 테스트의 Outbox 워커를 비활성화하며 테스트 소유 데이터만 정리 | [데이터 격리](./test-strategy.md#7-데이터-격리와-재현성) | [0010](./adr/0010-test-against-mysql-with-docker-compose.md), [0018](./adr/0018-isolate-test-database-and-outbox-workers.md) | `IT-DB-004`, `QT-CONFIG-003`, `QT-CONFIG-005`, 전체 `IT-*`, `EXT-*`, `CT-*` | 검증됨 | [Compose 초기화 스크립트](../docker/mysql/init/01-create-test-database.sh), 테스트 프로필과 개발 DB 비변경은 [#1 증거](#기반선-검증-증거-github-1), 작업별 fresh MySQL은 [#16 증거](#fresh-mysql-ci-검증-증거-github-16)로 검증하고, 워커 전용 Context 종료 격리는 [#18 증거](#최종-전체-리뷰-검증-증거-github-18)로 검증 |
| `POL-SCHEMA-01` | 멱등·Outbox 상태별 필수·NULL 조합과 타입·횟수 범위를 MySQL CHECK로 강제 | [ERD 목표 제약](./erd.md#5-목표-제약-조건) | [0020](./adr/0020-enforce-lifecycle-invariants-with-database-checks.md), [0023](./adr/0023-define-outbox-field-lifecycle.md) | `IT-DB-002`, `IT-DB-003`, `QT-SCHEMA-001` | 검증됨 | 상태별 조합·범위·대소문자·FK·UNIQUE·인덱스를 실제 MySQL에서 [#1 증거](#기반선-검증-증거-github-1)로 검증 |
| `POL-DEPS-01` | 기존 Web MVC·JPA·MySQL 외 추가 애플리케이션 의존성은 Validation·Actuator·Flyway와 제한된 Lombok만, 빌드 도구는 Spotless·Java 포매터만 승인하고 구조 검사·Mock·비동기·polling은 JDK 사용 | [승인된 기술 기준선](./architecture.md#2-승인된-기술-기준선), [테스트 환경](./test-strategy.md#41-환경-계약) | [0005](./adr/0005-establish-java-spring-mysql-platform-baseline.md), [0013](./adr/0013-use-actuator-and-micrometer-for-observability.md), [0019](./adr/0019-use-spotless-as-format-gate.md), [0022](./adr/0022-accept-user-id-without-authentication.md), [0027](./adr/0027-use-lombok-with-restrictions.md) | `QT-DEPS-001`, `QT-CONFIG-001`, `QT-FORMAT-001`, `QT-LOMBOK-001` | 구현 중 | 기존 승인 목록과 미승인 인증 의존성 부재는 [#1 증거](#기반선-검증-증거-github-1), Lombok scope와 유일 신규 의존성 검증은 작업 이슈 `#22`에서 추가 예정 |
| `POL-FORMAT-01` | `spotlessCheck`를 `check`에 포함하고 `spotlessApply`만 명시적으로 소스를 수정 | [테스트 실행 절차](./test-strategy.md#9-실행-절차) | [0019](./adr/0019-use-spotless-as-format-gate.md) | `QT-FORMAT-001` | 검증됨 | Spotless·Google Java Format과 `check` 연결을 [#1 증거](#기반선-검증-증거-github-1)로 검증 |

## 6. ADR 색인

활성 ADR은 프로젝트 소유자가 확인한 `승인됨` 상태이며 0002·0007과 인증 관련 0008·0012·0021은 후속 결정으로 대체됐다. ADR 0022는 0005의 Security 기준선, 0006의 Auth 모듈, 0015의 인증 실패 예외, 0018의 공유 JWT 키 조건도 부분 대체하고, ADR 0024는 0022의 기존 사용자 준비 절차 제외 조항만 부분 대체한다. ADR 0026은 0006을 유지하면서 기능 내부 계층과 공개 Facade 경계를 구체화한다. 상태와 부분 대체 관계의 정본은 [ADR 목록](./adr/)이다.

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
| [0026](./adr/0026-refine-feature-first-layered-architecture.md) | 기능 우선 계층형 아키텍처와 공개 Facade 경계 | FR-01~FR-05, NFR-03, POL-ARCH-01 |
| [0027](./adr/0027-use-lombok-with-restrictions.md) | 생성자·로거·JPA 기본 생성자에 제한된 Lombok | POL-LOMBOK-01, POL-DEPS-01 |

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
