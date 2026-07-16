# 0023. Outbox 상태별 필드 수명주기 확정

- 상태: 승인됨
- 날짜: 2026-07-16
- 결정자: 프로젝트 소유자

## 맥락

Outbox 이벤트는 재시도 중 마지막 실패 정보를 보존해야 하지만, 발행 성공 뒤에도 오류 정보나 다음 재시도 시각이 남으면 현재 상태의 의미가 모호해진다. 기존 문서는 상태별 claim과 완료 시각의 NULL 조합은 정했지만, 영구 실패의 `retry_count` 증가와 `next_retry_at`, `last_http_status`, `last_error_type`의 전체 수명주기를 닫지 않았다. Flyway CHECK, 상태 전이 코드와 테스트가 같은 규칙을 구현하도록 상태별 필드 계약을 확정할 필요가 있다.

## 고려한 대안

- 마지막 실패 정보를 모든 후속 상태에 계속 보존 — 장애 이력을 일부 확인할 수 있음 / `PUBLISHED`에도 오류가 남아 현재 성공 상태의 의미가 흐려짐
- 최종 상태에서도 `next_retry_at`을 유지 — 컬럼을 항상 `NOT NULL`로 둘 수 있음 / 더 이상 실행하지 않을 시각이 다음 재시도 시각으로 남음
- 재시도 중에만 마지막 실패를 보존하고 최종 상태를 정규화 — 현재 상태 해석과 CHECK가 명확함 / 성공 뒤 마지막 실패 상세는 `retry_count` 외에 남지 않음

## 결정

현재 `claim_token`으로 결과 갱신에 성공한 모든 실패 시도는 재시도 가능 여부와 관계없이 `retry_count`를 1 증가시킨다. lease 만료 뒤 이전 claim의 결과가 fencing으로 거절되면 상태와 횟수를 변경하지 않는다. 재시도할 수 있는 실패이고 시도가 남아 있으면 `PENDING`으로 전환하고 `next_retry_at`, `last_http_status`, `last_error_type`을 기록한다. 네트워크 오류와 timeout처럼 HTTP 응답이 없으면 `last_http_status`는 `NULL`이다.

재시도 `PENDING`과 이를 다시 선점한 `PROCESSING`은 이전 시도의 마지막 오류 정보를 보존한다. 최초 `PENDING`과 최초 `PROCESSING`에는 마지막 오류 정보가 없다. `PROCESSING`은 현재 `locked_at`과 `claim_token`을 반드시 가진다.

`PUBLISHED`로 전환할 때 `next_retry_at`, claim 정보, 실패 완료 시각과 마지막 오류 정보를 모두 `NULL`로 정리하고 `published_at`만 기록한다. 영구 오류가 발생하거나 네 번째 실패 시도까지 소진해 `FAILED`로 전환할 때 `next_retry_at`, claim 정보와 `published_at`을 `NULL`로 정리하고 `failed_at`, `last_error_type`, 선택적인 `last_http_status`를 보존한다.

이 상태별 NULL·필수 조합은 애플리케이션 상태 전이와 MySQL CHECK에서 함께 강제한다.

## 결과

- 현재 claim으로 반영된 모든 실패가 같은 방식으로 집계돼 `retry_count`의 의미가 결정적이다.
- 재시도 중에는 마지막 장애 원인을 진단할 수 있고, 성공한 이벤트에는 현재 의미와 충돌하는 오류 정보가 남지 않는다.
- 종결 상태의 `next_retry_at`이 `NULL`이므로 더 이상 실행할 예약이 없다는 뜻이 명확하다.
- 성공 전 마지막 장애 상세를 장기 감사해야 한다면 별도 시도 이력 모델을 새 결정으로 검토해야 한다.
