# 0026. 기능 우선 계층형 아키텍처와 공개 Facade 경계 구체화

- 상태: 승인됨
- 날짜: 2026-07-16
- 결정자: 프로젝트 소유자

## 맥락

ADR-0006은 하나의 배포 단위 안에서 기능을 먼저 나누고 기능 내부의 API·Application·Domain·Infrastructure 역할을 구분하기로 했다. 현재 구현은 기능별 최상위 패키지를 사용하지만 Controller와 Service, API 응답 DTO, JPA Entity, JDBC 저장 구현 사이의 경계가 물리적으로 드러나지 않고 Order가 다른 기능의 내부 타입을 직접 참조한다. 기존 트랜잭션·락·멱등 결과·Outbox 계약을 바꾸지 않으면서 기능 책임과 협력 경로를 명확히 할 필요가 있다.

## 고려한 대안

- 전역 controller·service·repository·entity 패키지 — 계층이 한눈에 보임 / 기능 응집도가 깨지고 ADR-0006의 경계를 약화함
- Gradle 멀티모듈 또는 마이크로서비스 — 컴파일·배포 경계를 강하게 강제함 / 현재 규모와 승인 범위를 넘어 빌드·운영 복잡도를 늘림
- 모든 클래스에 Interface·Impl과 Port·Adapter 쌍 추가 — 교체점을 형식적으로 만들 수 있음 / 실제 교체점이 없는 위임 계층과 타입을 증식시킴
- 기능 우선 패키지 안에서 필요한 계층만 분리하고 구조 테스트로 경계를 검증 — 기존 배포·데이터·트랜잭션을 유지함 / Java public 접근만으로는 경계가 보장되지 않아 지속적인 검사가 필요함

## 결정

ADR-0006을 유지하고 다음 규칙으로 구체화한다.

- 최상위 기능은 `menu`, `point`, `order`, `idempotency`, `outbox`로 유지하고 각 기능 안에서 실제 책임이 있는 `api`, `application`, `domain`, `infrastructure` 패키지만 만든다. 둘 이상의 기능이 실제로 재사용하는 안정된 코드는 `common`의 해당 역할에 둔다.
- 기본 의존 방향은 `api → application → domain`이다. 같은 기능의 Application은 해당 기능의 Infrastructure 저장소를 사용할 수 있고 Infrastructure는 Domain을 사용할 수 있다. Domain은 API·Application·Infrastructure와 Spring MVC·Jackson·JdbcTemplate에 의존하지 않는다.
- 다른 기능과의 협력은 대상 기능이 공개한 Application 계약만 사용한다. 다른 기능의 API DTO, Controller, Domain Entity, Repository, JDBC 저장소, HTTP Client를 직접 참조하지 않으며 기능 간 순환 의존을 만들지 않는다.
- Controller는 Request를 Command로, Application Result를 HTTP Response로 변환하고 Application Facade만 호출한다. Application과 Domain은 Spring MVC 타입을 사용하지 않는다.
- 기존 `OrderService`와 `PointService`처럼 이미 유스케이스와 트랜잭션을 조정하는 클래스는 별도 위임 Facade를 추가하지 않고 각각 `OrderFacade`, `PointFacade`로 승격한다. 한 메서드를 그대로 Service에 전달하는 Facade는 만들지 않는다.
- 메뉴 조회, 주문용 포인트 결제, 멱등성, Outbox 이벤트 추가와 전달은 책임이 드러나는 공개 Application 진입점을 제공한다. 인터페이스는 실제 기능 간 계약, 구현 교체점 또는 테스트 경계가 필요할 때만 사용한다.
- API Request·Response와 Application Command·Result를 분리하고 API Response를 기능 간 계약으로 재사용하지 않는다. 최초 HTTP 상태와 응답 본문을 그대로 저장·재사용하는 멱등 계약은 전용 codec 또는 assembler 경계로 격리한다.
- 물리적 분리가 오히려 가독성을 떨어뜨리는 작은 기능은 빈 패키지나 한 줄 타입을 만들지 않고 package-private와 명명 규칙을 사용할 수 있으며, 예외를 구조 문서에 기록한다.
- JUnit과 JDK 파일 API로 의존 방향, Controller→Facade, 기능 간 Application-only 협력과 순환 부재를 검증한다. ArchUnit과 Spring Modulith는 도입하지 않는다.

## 결과

- 기능 내부 책임과 기능 간 공개 경계가 소스 경로와 구조 테스트에 드러난다.
- 기존 단일 프로세스·단일 데이터베이스·트랜잭션·REST API·Flyway 스키마는 유지된다.
- Facade와 계층 타입 수가 실제 유스케이스 경계만큼 늘며, 작은 기능은 물리적 계층을 생략할 수 있다.
- Java 접근 제한만으로 기능 경계를 완전히 막을 수 없으므로 구조 테스트와 리뷰를 계속 유지해야 한다.
- 되돌리려면 패키지와 클래스명을 다시 합치고 구조 테스트를 제거할 수 있으나 공개 Application 계약을 참조하는 호출부를 함께 복원해야 한다.
