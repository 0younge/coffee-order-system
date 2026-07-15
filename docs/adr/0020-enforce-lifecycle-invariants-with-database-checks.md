# 0020. 상태 수명주기 불변식을 DB CHECK로 강제

- 상태: 승인됨
- 날짜: 2026-07-15
- 결정자: 프로젝트 소유자

## 맥락

멱등 레코드와 Outbox 이벤트는 상태에 따라 필수 컬럼과 비어 있어야 하는 컬럼이 달라진다. 애플리케이션 코드와 테스트만으로 이 조합을 보장하면 경쟁 조건, 잘못된 SQL이나 이후 유지보수 변경이 모순된 상태를 커밋할 수 있다. MySQL을 데이터의 단일 진실 원천으로 사용하는 설계에 맞춰 명확한 상태 불변식은 DB에서도 보호해야 한다.

## 고려한 대안

- 애플리케이션 검증만 사용 — DDL이 단순함 / 코드 밖의 쓰기와 회귀가 모순된 상태를 저장할 수 있음
- 단일 컬럼 범위만 CHECK — 상태·횟수 값은 제한함 / 상태와 결과 컬럼의 조합은 보호하지 못함
- 단일 컬럼과 핵심 상태 조합을 CHECK — 잘못된 커밋을 DB가 거절함 / 마이그레이션과 상태 전이 SQL이 더 엄격해짐

## 결정

애플리케이션 검증과 함께 MySQL CHECK 제약으로 다음 불변식을 강제한다.

- 상태·작업 유형·이벤트 유형의 허용 값
- 포인트 잔액 비음수, 가격·결제금액 양수, Outbox `retry_count` 0~4
- 멱등 `PROCESSING`의 결과 필드와 `completed_at`은 `NULL`
- 멱등 `COMPLETED`의 결과 필드와 `completed_at`은 `NOT NULL`
- Outbox `PROCESSING`의 `claim_token`, `locked_at`은 `NOT NULL`
- Outbox `PUBLISHED`의 `published_at`은 `NOT NULL`이고 claim 정보와 `failed_at`은 `NULL`
- Outbox `FAILED`의 `failed_at`, `last_error_type`은 `NOT NULL`이고 claim 정보와 `published_at`은 `NULL`
- Outbox `PENDING`은 claim 정보와 완료 시각이 `NULL`

상태 전이 서비스는 DB 제약을 정상 제어 흐름의 대체물로 사용하지 않는다. 애플리케이션이 먼저 올바른 상태를 만들고 실제 MySQL 통합 테스트가 각 허용·거절 조합을 검증한다.

## 결과

- 모순된 멱등 결과와 Outbox 완료 상태가 커밋되는 것을 DB가 마지막으로 방어한다.
- Flyway DDL, JPA 매핑과 상태 전이 SQL이 같은 NULL·범위 계약을 따라야 한다.
- 상태 모델을 확장할 때 기존 CHECK와 테스트를 함께 변경해야 한다.
- MySQL과 다른 대체 DB로 테스트하면 제약 동작이 달라질 수 있으므로 실제 MySQL 검증을 유지한다.
