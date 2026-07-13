# Coffee Order System

다중 애플리케이션 인스턴스 환경에서도 포인트 잔액과 주문 데이터의 정합성을 지키는 커피 주문 REST API 백엔드입니다. 사용자는 포인트를 충전해 메뉴 하나를 주문하며, 시스템은 결제 완료 주문을 외부 데이터 수집 API로 비동기 전달하고 최근 7일 인기 메뉴를 제공합니다.

> **문서와 구현 상태**
>
> 이 저장소의 문서는 목표 계약 초안을 설명합니다. ADR별 승인 여부는 [ADR 목록](./docs/adr/)에서 관리합니다. 현재 코드는 Spring Boot 애플리케이션 부트스트랩, 기본 컨텍스트 테스트, MySQL Docker Compose와 DB 연결 설정만 존재하며, 도메인 기능·DB 스키마·Flyway·외부 연동 워커는 아직 구현되지 않았습니다. 문서에 적힌 동작을 현재 구현 완료 상태로 해석하지 않습니다.

## 제품 범위

- 이메일과 비밀번호로 가입하고 JWT Access Token으로 인증합니다.
- 인증된 사용자가 자신의 포인트를 양의 정수만큼 충전합니다.
- 인증된 사용자가 메뉴 하나를 포인트로 주문하고 결제합니다.
- 결제 완료 주문을 Transactional Outbox로 외부 데이터 수집 API에 전달합니다.
- 조회 시점 직전 7×24시간 동안의 결제 완료 주문을 집계해 인기 메뉴 상위 3개를 반환합니다.

상세 요구사항과 수용 기준은 [PRD](./docs/prd.md), 요청·응답 계약은 [API 명세](./docs/api-spec.md)를 기준으로 합니다.

## 핵심 처리 흐름

| 흐름 | 처리 요약 | 정합성 경계 |
|---|---|---|
| 포인트 충전 | 멱등 요청 확인 → 사용자 행 잠금 → 잔액 증가 → 최초 결과 저장 | 비즈니스 변경과 멱등 레코드를 하나의 MySQL 트랜잭션으로 커밋 |
| 주문 및 결제 | 멱등 요청 확인 → 사용자 행 잠금 → 현재 메뉴 조회 → 잔액 차감 → 주문 스냅샷과 Outbox 저장 | 포인트·주문·Outbox·멱등 결과를 하나의 MySQL 트랜잭션으로 커밋 |
| 주문 이벤트 전달 | 워커가 이벤트 선점 → DB 트랜잭션 밖에서 외부 호출 → 성공 또는 재시도 상태 저장 | `SKIP LOCKED`, `claim_token`, 30초 lease로 다중 워커의 갱신을 fencing |
| 인기 메뉴 조회 | `PAID` 주문의 최근 7×24시간 주문 수 집계 → 현재 메뉴 정보 결합 | 주문 수는 `orders`, 응답 이름·가격은 현재 `menus`가 기준 |

예상 가능한 비즈니스 실패는 해당 결과를 멱등 레코드에 저장하고 커밋합니다. DB 연결 실패, 5초 락 대기 timeout과 deadlock 같은 인프라 오류는 비즈니스 변경과 멱등 레코드를 모두 롤백하므로 같은 요청을 다시 시도할 수 있습니다. 자세한 상태 전이와 재사용 규칙은 [API 명세의 멱등성 계약](./docs/api-spec.md#10-멱등성-계약)을 따릅니다.

## 핵심 불변식

1. `users.point_balance`는 어떤 커밋 시점에도 음수가 될 수 없습니다.
2. 같은 사용자의 충전과 결제는 사용자 행 비관적 락으로 직렬화합니다.
3. 같은 사용자·작업 유형에서 하나의 `Idempotency-Key`는 하나의 요청 내용에만 대응합니다.
4. 주문 한 건은 메뉴 하나만 포함하며, 주문 시점의 메뉴명과 결제금액은 변경하지 않습니다.
5. 주문이 커밋되면 같은 트랜잭션에 대응하는 `ORDER_PAID` Outbox 이벤트가 정확히 하나 존재합니다.
6. 외부 이벤트 전달은 at-least-once입니다. 수신 측은 `eventId`로 중복 반영을 방지해야 합니다.
7. 인기 메뉴의 주문 수는 `PAID` 주문만 집계하고, 동률이면 메뉴 ID 오름차순으로 순위를 결정합니다.

데이터 구조와 제약 조건은 [ERD](./docs/erd.md), 모듈과 트랜잭션 흐름은 [아키텍처](./docs/architecture.md)에 상세히 설명합니다.

## 기술 기준선

| 영역 | 확정 기준 |
|---|---|
| Java | Amazon Corretto 17 호환 Java 17 |
| 애플리케이션 | Spring Boot 4.1.0, Spring Web MVC, Spring Security, Spring Data JPA |
| 빌드 | Gradle 9.5.1 Wrapper |
| 데이터베이스 | MySQL 8.4 LTS |
| 스키마 변경 | Flyway |
| 인증 | BCrypt 비밀번호 해시, HS256 30분 JWT Access Token |
| 테스트 | JUnit 5, 실제 MySQL 기반 통합 테스트, 외부 API Mock 서버 |
| 로컬 인프라 | Docker Compose로 MySQL만 실행; 애플리케이션은 호스트에서 Gradle로 실행 |

버전 선택의 근거는 [ADR-0005](./docs/adr/0005-establish-java-spring-mysql-platform-baseline.md), 애플리케이션 구성 방식은 [ADR-0006](./docs/adr/0006-use-feature-oriented-modular-monolith.md)를 참고합니다.

## 로컬 실행 계약

다음 순서로 Docker Compose의 MySQL 8.4 LTS 인스턴스를 준비한 뒤 테스트하고 애플리케이션을 실행합니다.

```bash
docker compose up -d --wait
./gradlew test
./gradlew bootRun
```

- Docker Compose에는 MySQL 서비스만 포함합니다. 애플리케이션 컨테이너는 만들지 않습니다.
- 기본 데이터베이스는 `coffee_order_system`, 사용자명과 비밀번호는 `coffee`입니다.
- 기존 로컬 MySQL과의 충돌을 피하기 위해 기본 호스트 포트는 `3307`입니다.
- 데이터베이스 이름은 `DB_NAME`, 호스트 포트는 `DB_PORT`, 애플리케이션 JDBC URL은 `DB_URL`, 사용자명은 `DB_USERNAME`, 비밀번호는 `DB_PASSWORD`로 재정의할 수 있습니다. MySQL root 비밀번호는 `DB_ROOT_PASSWORD`로 재정의합니다.
- Flyway 구현 후 애플리케이션 시작 시 스키마와 초기 메뉴를 적용합니다.
- 통합 테스트는 인메모리 DB로 대체하지 않고 실제 MySQL과의 SQL·락·제약 조건 동작을 검증합니다.

완료 판정 절차와 테스트 범위는 [테스트 전략](./docs/test-strategy.md)을 따릅니다.

## 문서 지도

| 문서 | 책임 |
|---|---|
| [도메인 컨텍스트](./docs/context.md) | 공통 용어, 데이터 출처, 컨텍스트·트랜잭션 경계 |
| [PRD](./docs/prd.md) | 기능별 목적, 선행 조건, 후조건, 수용 기준과 비기능 요구사항 |
| [API 명세](./docs/api-spec.md) | HTTP 요청·응답 필드, 오류 코드, 멱등성과 외부 연동 계약 |
| [ERD](./docs/erd.md) | 테이블 관계, 필드, 제약 조건과 데이터 불변식 |
| [아키텍처](./docs/architecture.md) | 모듈 구조, 동시성 제어, Outbox 처리 흐름과 장애 대응 |
| [테스트 전략](./docs/test-strategy.md) | 테스트 계층, 동시성·원자성·외부 연동 검증 방법 |
| [요구사항 추적표](./docs/requirements-traceability.md) | 요구사항과 설계·테스트 근거의 연결 |

### Architecture Decision Records

아래 ADR은 모두 프로젝트 소유자가 확인한 **승인됨(Accepted)** 상태입니다.

1. [ADR-0001: 포인트 변경에 DB 비관적 락 사용](./docs/adr/0001-use-database-pessimistic-locking-for-points.md)
2. [ADR-0002: 충전과 주문에 멱등키 적용](./docs/adr/0002-protect-mutations-with-idempotency-keys.md)
3. [ADR-0003: 주문 이벤트를 Transactional Outbox로 전달](./docs/adr/0003-deliver-order-events-with-transactional-outbox.md)
4. [ADR-0004: 인기 메뉴를 결제 완료 주문에서 직접 집계](./docs/adr/0004-calculate-popular-menus-from-paid-orders.md)
5. [ADR-0005: Java·Spring·MySQL 플랫폼 기준선 확립](./docs/adr/0005-establish-java-spring-mysql-platform-baseline.md)
6. [ADR-0006: 기능 중심 모듈러 모놀리스 사용](./docs/adr/0006-use-feature-oriented-modular-monolith.md)
7. [ADR-0007: 원장 없이 현재 포인트 잔액 저장](./docs/adr/0007-store-current-point-balance-without-ledger.md)
8. [ADR-0008: 무상태 JWT Access Token 인증 사용](./docs/adr/0008-use-stateless-jwt-access-token-authentication.md)
9. [ADR-0009: 주문을 단일 메뉴와 스냅샷으로 모델링](./docs/adr/0009-model-single-menu-orders-with-snapshots.md)
10. [ADR-0010: Docker Compose의 MySQL로 통합 테스트](./docs/adr/0010-test-against-mysql-with-docker-compose.md)
11. [ADR-0011: DB 경합 timeout과 deadlock에 일시적 오류 반환](./docs/adr/0011-return-temporary-unavailable-on-database-contention.md)

## 의도적으로 제외한 범위

- 프론트엔드와 관리자 화면
- 메뉴 등록·수정·삭제 API
- 다품목 주문, 수량, 장바구니
- 주문 취소·환불
- 포인트 원장
- Refresh Token, 로그아웃 토큰 폐기, OAuth, 계정 복구
- Redis·Kafka 기반 잠금 또는 인기 메뉴 사전 집계
- 실제 클라우드 배포와 Kubernetes·Terraform

제외 범위가 제품 요구사항으로 바뀌면 기존 데이터 모델이나 정합성 경계를 바로 수정하지 않고, 영향과 대안을 먼저 ADR로 검토합니다.
