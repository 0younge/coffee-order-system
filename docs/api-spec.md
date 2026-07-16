# API 명세

## 1. 문서 상태

이 문서는 Coffee Order System의 목표 HTTP 계약입니다. 현재 저장소는 공통 응답 경계와 메뉴·포인트·주문·인기 메뉴 API, Outbox 외부 전송 계약을 구현하고 자동화 테스트로 검증했습니다. 이 문서의 필드명·HTTP 상태·응답 코드는 구현과 테스트에서 동일하게 유지합니다.

도메인 의미는 [CONTEXT](./context.md), 기능 수용 기준은 [PRD](./prd.md), DB 필드는 [ERD](./erd.md)를 함께 참고합니다.

## 2. 공통 규칙

| 항목 | 계약 |
|---|---|
| Base path | `/api/v1` |
| 요청 Content-Type | 본문이 있는 `POST`는 `application/json`; 본문 없는 `GET`은 요구하지 않음 |
| 응답 Content-Type | 직렬화 가능한 업무·라우팅 응답은 `application/json`; JSON을 허용하지 않는 `Accept`의 `406`만 빈 본문 |
| 충전·주문 멱등성 | UUID 문자열 `Idempotency-Key` 필수 |
| 사용자 식별 | 충전·주문 요청 본문의 기존 사용자 `userId` |
| 금액 단위 | 정수 포인트(P), 1원 = 1P |
| 시간 | UTC 기준 ISO 8601 문자열. 주문·주문 이벤트 시각은 최대 microsecond 정밀도(예: `2026-07-11T12:00:00.123456Z`) |

원문 요구사항의 "사용자 식별값"은 충전·주문 요청 본문의 `userId`에 대응합니다. `userId`는 시스템에 이미 존재하는 사용자를 가리켜야 하며 사용자 생성과 소유권 검증은 현재 범위에 포함하지 않습니다. 과제·로컬 수동 검증용 Flyway 기준 사용자는 `userId=1`, 초기 잔액은 `0P`이며 자동화 테스트는 이 행에 의존하지 않습니다. 그 밖의 숫자 ID와 날짜는 예시일 뿐 고정값이 아닙니다. 사용자 ID와 멱등키의 구체적인 검증 규칙은 [입력 검증 규칙](#11-입력-검증-규칙)을 따릅니다. 알 수 없는 JSON 필드, 문자열에서 숫자로의 자동 형 변환, 필수 필드의 누락·`null`은 허용하지 않습니다.

모든 업무 API는 별도 인증 없이 호출합니다. Actuator는 상세 정보를 숨긴 `/actuator/health`만 HTTP에 노출합니다.

### 입력 검증과 오류 우선순위

복합 오류에서도 구현과 테스트 결과가 달라지지 않도록 다음 순서를 사용합니다.

1. HTTP 헤더·`Content-Type`·JSON 문법·필수 필드·필드 타입과 숫자 범위를 검증합니다. 지원하지 않는 요청 미디어 타입은 `415`, 나머지 검증 실패는 `400`이며 DB를 조회하거나 멱등 결과를 저장하지 않습니다.
2. 충전·주문의 기존 사용자를 확인합니다. 없으면 `404 USER_NOT_FOUND`이며 멱등 결과를 저장하지 않습니다.
3. 멱등키를 선점하거나 완료 결과를 재사용합니다. 같은 키의 요청 지문이 다르면 이후 업무 규칙을 확인하지 않고 `409 IDEMPOTENCY_KEY_REUSED`를 반환합니다.
4. 최초 요청만 업무 규칙을 검사합니다. 주문은 메뉴 존재 여부를 먼저, 포인트 잔액을 다음으로 확인합니다. 충전은 사용자 행을 잠근 뒤 덧셈 overflow를 확인합니다.
5. DB timeout·deadlock과 예상하지 못한 인프라 오류는 앞선 공개 오류가 확정되지 않은 경우에만 해당 오류 계약으로 변환합니다.

따라서 주문에서 사용자와 메뉴가 모두 없으면 `USER_NOT_FOUND`, 멱등키가 재사용됐고 메뉴도 없으면 `IDEMPOTENCY_KEY_REUSED`, 메뉴가 없고 잔액도 부족하면 `MENU_NOT_FOUND`가 우선합니다. 완료된 같은 멱등 요청은 현재 업무 상태를 다시 검사하지 않고 저장된 최초 결과를 반환합니다.

### 공통 응답 봉투

| 필드 | 타입 | 항상 존재 | 의미 |
|---|---|---:|---|
| `success` | boolean | 예 | 요청의 성공 여부 |
| `code` | string | 예 | 클라이언트가 분기할 안정적인 결과 코드 |
| `message` | string | 예 | 사람이 읽는 결과 설명 |
| `data` | object, array, null | 예 | 성공 데이터. 실패 시 `null` |

성공 예시:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {}
}
```

실패 예시:

```json
{
  "success": false,
  "code": "INSUFFICIENT_POINT",
  "message": "포인트 잔액이 부족합니다.",
  "data": null
}
```

HTTP 상태 코드는 본문 코드와 별개로 의미에 맞게 사용하며, 클라이언트는 `code`로 구체적인 결과를 구분합니다.

정의되지 않은 경로의 `404 RESOURCE_NOT_FOUND`와 지원하지 않는 메서드의 `405 METHOD_NOT_ALLOWED`도 같은 실패 봉투를 사용합니다. `405`는 해당 경로가 지원하는 메서드를 `Allow` 헤더로 함께 반환합니다. 요청의 `Accept`가 `application/json`을 허용하지 않아 발생한 `406 Not Acceptable`은 JSON 오류 본문 자체를 협상할 수 없으므로 공통 봉투의 유일한 예외이며 `Content-Type`과 본문 없이 반환합니다.

## 3. API 목록

| 기능 | Method | Path | 멱등키 |
|---|---|---|---|
| 메뉴 목록 | GET | `/menus` | 불필요 |
| 포인트 충전 | POST | `/points/charge` | 필요 |
| 주문 및 결제 | POST | `/orders` | 필요 |
| 인기 메뉴 | GET | `/menus/popular` | 불필요 |

## 4. 메뉴 목록 조회

`GET /api/v1/menus`

요청 본문과 쿼리 파라미터가 없습니다.

### 성공 응답

`200 OK`

| `data[]` 필드 | 타입 | 의미 |
|---|---|---|
| `menuId` | integer | 메뉴 ID |
| `name` | string | 조회 시점의 현재 메뉴명 |
| `price` | integer | 조회 시점의 현재 포인트 가격 |

결과 배열은 `menuId` 오름차순입니다.

```json
{
  "success": true,
  "code": "MENUS_RETRIEVED",
  "message": "메뉴 목록을 조회했습니다.",
  "data": [
    { "menuId": 1, "name": "아메리카노", "price": 4000 },
    { "menuId": 2, "name": "카페라떼", "price": 5000 },
    { "menuId": 3, "name": "카푸치노", "price": 5500 }
  ]
}
```

## 5. 포인트 충전

`POST /api/v1/points/charge`

### 요청 헤더

| 헤더 | 필수 | 의미 |
|---|---:|---|
| `Idempotency-Key` | 예 | 하나의 논리적 충전 요청에 재사용할 클라이언트 생성 UUID 문자열 |

```text
Idempotency-Key: 3f50f3c8-25c8-4a11-a182-2fb9f2d46e68
```

### 요청 필드

| 필드 | 타입 | 필수 | 검증·의미 |
|---|---|---:|---|
| `userId` | integer | 예 | signed `BIGINT` 범위의 0보다 큰 기존 사용자 ID |
| `amount` | integer | 예 | signed `BIGINT` 범위의 0보다 큰 충전 포인트. 소수·범위 밖 값 허용 안 함 |

```json
{ "userId": 1, "amount": 10000 }
```

### 성공 응답

`200 OK`

| `data` 필드 | 타입 | 의미 |
|---|---|---|
| `chargedAmount` | integer | 실제 반영된 충전 금액 |
| `balance` | integer | 충전 트랜잭션 커밋 후의 사용자 잔액 |

```json
{
  "success": true,
  "code": "POINT_CHARGED",
  "message": "포인트를 충전했습니다.",
  "data": {
    "chargedAmount": 10000,
    "balance": 10000
  }
}
```

### 실패 계약

- 사용자 부재: `404 USER_NOT_FOUND`; 멱등 결과를 저장하지 않음
- 금액이 정수가 아니거나 0 이하이거나 signed `BIGINT` 범위를 벗어남: `400 INVALID_CHARGE_AMOUNT`
- 현재 잔액과 충전금액의 합이 signed `BIGINT` 범위를 벗어남: `409 POINT_BALANCE_OVERFLOW`
- 필수 헤더·필드 누락 또는 그 밖의 형식 오류: `400 INVALID_REQUEST`
- 같은 멱등키에 다른 `amount` 사용: `409 IDEMPOTENCY_KEY_REUSED`
- DB 락 대기 5초 초과 또는 deadlock: `503 TEMPORARILY_UNAVAILABLE`
- 모든 실패에서 포인트 잔액은 변경되지 않습니다. 멱등 레코드의 커밋·롤백 범위는 [멱등성 계약](#8-멱등성-계약)을 따릅니다.

## 6. 주문 및 결제

`POST /api/v1/orders`

### 요청 헤더

| 헤더 | 필수 | 의미 |
|---|---:|---|
| `Idempotency-Key` | 예 | 하나의 논리적 주문 요청에 재사용할 클라이언트 생성 UUID 문자열 |

```text
Idempotency-Key: 9324b129-5cc7-4fb2-8b8d-8a3c48617453
```

### 요청 필드

| 필드 | 타입 | 필수 | 검증·의미 |
|---|---|---:|---|
| `userId` | integer | 예 | signed `BIGINT` 범위의 0보다 큰 기존 사용자 ID |
| `menuId` | integer | 예 | signed `BIGINT` 범위의 0보다 큰 메뉴 ID. 존재하지 않으면 `MENU_NOT_FOUND` |

```json
{ "userId": 1, "menuId": 1 }
```

### 성공 응답

`201 Created`

| `data` 필드 | 타입 | 데이터 출처·의미 |
|---|---|---|
| `orderId` | integer | 생성된 주문 ID |
| `menuId` | integer | 주문한 메뉴 ID |
| `menuName` | string | 주문 시점의 `menus.name`을 보존한 스냅샷 |
| `paidAmount` | integer | 주문 시점의 `menus.price`로 확정한 결제 포인트 |
| `remainingBalance` | integer | 결제 트랜잭션 커밋 후 사용자 잔액 |
| `paidAt` | string(date-time) | 결제 완료 시각, ISO 8601 UTC |

```json
{
  "success": true,
  "code": "ORDER_PAID",
  "message": "주문과 결제가 완료되었습니다.",
  "data": {
    "orderId": 101,
    "menuId": 1,
    "menuName": "아메리카노",
    "paidAmount": 4000,
    "remainingBalance": 6000,
    "paidAt": "2026-07-11T12:00:00Z"
  }
}
```

### 성공 시 원자성

다음 변경은 하나의 MySQL 트랜잭션으로 함께 커밋합니다.

1. 사용자 포인트 차감
2. `PAID` 주문과 메뉴명·결제금액 스냅샷 저장
3. 대응하는 `ORDER_PAID` Outbox 이벤트 저장
4. 최초 HTTP 상태와 응답 본문을 가진 멱등 레코드 `COMPLETED` 전환

### 실패 계약

- 사용자 부재: `404 USER_NOT_FOUND`; 멱등 결과를 저장하지 않음
- 메뉴 부재: `404 MENU_NOT_FOUND`
- 포인트 부족: `409 INSUFFICIENT_POINT`
- 같은 멱등키에 다른 `menuId` 사용: `409 IDEMPOTENCY_KEY_REUSED`
- 그 밖의 형식 또는 필드 검증 실패: `400 INVALID_REQUEST`
- DB 락 대기 5초 초과 또는 deadlock: `503 TEMPORARILY_UNAVAILABLE`
- 메뉴 부재와 포인트 부족은 예상 가능한 비즈니스 실패입니다. 포인트·주문·Outbox는 변경하지 않고 실패 HTTP 상태와 본문을 멱등 결과로 커밋합니다.
- 인프라 오류는 포인트·주문·Outbox·멱등 레코드를 모두 롤백합니다.

## 7. 인기 메뉴 조회

`GET /api/v1/menus/popular`

요청 본문과 쿼리 파라미터가 없습니다.

### 집계 계약

- 구간은 `[조회 시각 - 7일, 조회 시각)`입니다.
- 하한은 포함하고 상한은 제외합니다.
- 구간 안의 `orders.status = PAID`인 주문 수를 `menuId`별로 집계합니다.
- 주문 수 내림차순, 동률이면 `menuId` 오름차순으로 정렬해 최대 3개를 반환합니다.
- `orderCount`는 결제 완료 주문에서 계산합니다.
- `name`, `price`는 주문 스냅샷이 아니라 조회 시점의 현재 `menus`에서 가져옵니다.
- 대상 메뉴가 3개 미만이면 존재 항목만, 대상이 없으면 빈 배열을 반환합니다.

### 성공 응답

`200 OK`

| `data[]` 필드 | 타입 | 데이터 출처·의미 |
|---|---|---|
| `rank` | integer | 정렬 후 1부터 시작하는 순위 |
| `menuId` | integer | 집계한 메뉴 ID |
| `name` | string | 현재 `menus.name` |
| `price` | integer | 현재 `menus.price` |
| `orderCount` | integer | 기간 안의 `PAID` 주문 수 |

```json
{
  "success": true,
  "code": "POPULAR_MENUS_RETRIEVED",
  "message": "인기 메뉴를 조회했습니다.",
  "data": [
    {
      "rank": 1,
      "menuId": 1,
      "name": "아메리카노",
      "price": 4000,
      "orderCount": 42
    }
  ]
}
```

## 8. 멱등성 계약

### 적용 범위와 키 의미

- 적용 API: 포인트 충전, 주문 및 결제
- 유일 범위: `(요청 userId, 작업 유형, Idempotency-Key)`
- 키 형식: UUID 문자열. 파싱한 UUID의 표준 소문자 문자열을 저장합니다.
- 클라이언트는 하나의 논리 요청과 그 네트워크 재시도에 같은 키를 사용하고, 다른 논리 요청에는 새 키를 사용합니다.
- 서버는 `CHARGE|<amount>` 또는 `ORDER|<menuId>`의 UTF-8 SHA-256 `request_hash`를 저장해 같은 키의 요청 내용이 동일한지 비교합니다.

### 최초 요청과 재요청

| 상황 | 처리 | 공개 결과 |
|---|---|---|
| 처음 본 키 | 기존 사용자를 확인한 뒤 멱등 레코드를 `PROCESSING`으로 만들고 비즈니스 처리 | 성공 또는 명세된 실패 결과로 진행 |
| 같은 키·같은 요청, 최초 결과 확정 | 저장한 `http_status`, `result_code`, `response_body` 재사용 | 최초 HTTP 상태와 응답 본문 반환 |
| 같은 키·다른 요청 | 비즈니스 처리하지 않음 | `409 IDEMPOTENCY_KEY_REUSED` |
| 최초 처리 중 인프라 오류 | 전체 트랜잭션과 멱등 레코드 롤백 | `500 INTERNAL_SERVER_ERROR`; 같은 논리 요청 재시도 가능 |
| DB 락 대기 5초 초과 또는 deadlock | 전체 트랜잭션과 멱등 레코드 롤백 | `503 TEMPORARILY_UNAVAILABLE`; 같은 키로 재시도 가능 |

### 트랜잭션과 상태

1. 멱등 레코드 생성, 포인트·주문 변경, 결과 저장은 해당 API의 하나의 DB 트랜잭션 안에서 처리합니다.
2. 성공하거나 메뉴 부재·포인트 부족·포인트 덧셈 overflow로 결론 나면 결과 코드·HTTP 상태·응답 본문을 저장하고 `COMPLETED`로 전환한 뒤 커밋합니다.
3. 비즈니스 실패에서는 포인트·주문·Outbox를 변경하지 않지만 실패 멱등 결과는 커밋합니다.
4. DB 연결·커밋 등 예상하지 못한 인프라 오류는 도메인 변경과 멱등 레코드를 모두 롤백합니다.
5. DB 유일 제약으로 동시 최초 요청을 하나만 처리합니다. 승자 트랜잭션이 커밋되면 같은 요청은 그 결과를 재사용하고, 승자가 롤백되면 후속 요청이 다시 처리할 수 있습니다.
6. 사용자 부재, 멱등키 누락·형식 오류, JSON 오류, 필수 필드 누락과 잘못된 충전 금액은 멱등 결과로 저장하지 않습니다.

DB 락 대기는 최대 5초입니다. timeout이나 deadlock의 `503` 결과는 멱등 저장하지 않으므로 클라이언트는 반드시 같은 키로 재시도합니다.

### 보존

- `COMPLETED` 레코드는 `completed_at`부터 최소 24시간 보존하고 그 이후 삭제할 수 있습니다.
- 보존 기간 안에는 같은 키·같은 요청의 최초 결과 재사용을 보장합니다.
- 자동 정리 시각은 보장하지 않습니다. 실제로 정리된 키에는 서버가 이전 결과를 보장할 수 없으므로 클라이언트는 이전 키를 새 논리 요청에 재사용하지 않습니다.

JSON 원문, 공백과 필드 순서는 요청 해시 입력에 포함하지 않습니다.

## 9. 오류 코드

| HTTP | Code | 적용 조건 |
|---:|---|---|
| 400 | `INVALID_REQUEST` | 필수 헤더·필드 누락, JSON 형식 또는 일반 필드 검증 실패 |
| 400 | `INVALID_CHARGE_AMOUNT` | 충전 금액이 정수가 아니거나 0 이하이거나 signed `BIGINT` 범위 밖 |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | 본문이 있는 요청의 `Content-Type`이 `application/json`이 아님 |
| 404 | `RESOURCE_NOT_FOUND` | 정의되지 않은 요청 경로 |
| 404 | `USER_NOT_FOUND` | 충전·주문의 사용자 ID가 존재하지 않음 |
| 404 | `MENU_NOT_FOUND` | 주문의 메뉴가 존재하지 않음 |
| 405 | `METHOD_NOT_ALLOWED` | 경로는 존재하지만 요청 HTTP 메서드를 지원하지 않음; `Allow` 헤더 포함 |
| 409 | `INSUFFICIENT_POINT` | 주문 결제 잔액 부족 |
| 409 | `POINT_BALANCE_OVERFLOW` | 현재 잔액과 유효한 충전금액의 합이 signed `BIGINT` 범위를 초과 |
| 409 | `IDEMPOTENCY_KEY_REUSED` | 같은 사용자·작업의 같은 키에 다른 요청 내용 사용 |
| 500 | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버·인프라 오류 |
| 503 | `TEMPORARILY_UNAVAILABLE` | DB 락 대기 5초 초과 또는 deadlock |

오류 응답의 `success`는 `false`, `data`는 `null`입니다. 예상하지 못한 오류의 내부 예외·SQL·자격 증명을 응답에 노출하지 않습니다.

`406 Not Acceptable`은 위 오류 코드 표와 공통 봉투의 예외로, `Content-Type`과 본문 없이 반환합니다.

## 10. 외부 데이터 수집 API 계약

수신 시스템은 이 저장소에서 구현하지 않으며 통합 테스트에서는 JDK Mock HTTP API로 대체합니다. base URL은 기본값 없는 `COLLECTION_API_BASE_URL` 환경 변수로 주입합니다. HTTP(S) URL의 명시 포트는 `1~65535`만 허용하고 기존 path를 버리지 않은 채 그 뒤에 `events/orders`를 결합합니다. 누락되거나 잘못되면 애플리케이션 시작이 실패하며 현재 범위에서는 별도 자격 증명 헤더를 전송하지 않습니다.

### 요청

`POST /events/orders`

| 필드 | 타입 | 필수 | 데이터 출처·의미 |
|---|---|---:|---|
| `eventId` | string | 예 | Outbox 이벤트의 고유 ID. 모든 재시도에서 동일하며 수신 측 중복 제거 키 |
| `eventType` | string | 예 | 고정값 `ORDER_PAID` |
| `occurredAt` | string(date-time) | 예 | 결제 완료 사건 발생 시각, ISO 8601 UTC |
| `userId` | integer | 예 | 주문한 사용자 ID |
| `menuId` | integer | 예 | 주문한 메뉴 ID |
| `paymentAmount` | integer | 예 | 주문에 저장한 결제 포인트 `paid_amount` |

```json
{
  "eventId": "cc49d0c4-ea2b-4adf-9121-e9719f4ee3a7",
  "eventType": "ORDER_PAID",
  "occurredAt": "2026-07-11T12:00:00Z",
  "userId": 1,
  "menuId": 1,
  "paymentAmount": 4000
}
```

전달은 at-least-once이므로 HTTP 응답 유실이나 워커 복구 시 같은 `eventId`가 다시 전송될 수 있습니다. 수신 측은 `eventId`를 멱등키로 사용해야 합니다.

### Outbox 선점과 fencing

1. 워커는 전송 가능 시각이 된 이벤트를 짧은 DB 트랜잭션에서 `FOR UPDATE SKIP LOCKED` 방식으로 조회합니다.
2. 선점한 행을 `PROCESSING`으로 바꾸고 새로운 `claim_token`, 현재 `locked_at`을 기록한 뒤 커밋합니다.
3. 외부 HTTP 호출은 DB 트랜잭션 밖에서 수행합니다.
4. 결과 갱신은 `event_id`, 현재 `claim_token`, `PROCESSING` 상태가 모두 일치할 때만 수행합니다.
5. 30초 lease가 만료된 이벤트는 다른 워커가 새 `claim_token`으로 다시 선점할 수 있습니다. 이전 워커는 더 이상 결과를 덮어쓸 수 없습니다.
6. 워커는 활성 배치가 없을 때 기본 1초마다 `next_retry_at`, `created_at`, `event_id` 오름차순으로 최대 50건을 선점하고 JDK 비동기 HTTP 클라이언트로 병렬 전송합니다.
7. 워커가 정상이고 전송 가능한 기존 backlog가 없으면 주문 커밋 후 2초 이내에 최초 외부 HTTP 요청을 시작합니다. 이 기준은 외부 응답 완료나 `PUBLISHED` 전환 시간이 아닙니다.

### timeout과 응답 분류

| 결과 | 분류 | Outbox 처리 |
|---|---|---|
| HTTP `2xx` | 성공 | `PUBLISHED`, `published_at` 기록 |
| 네트워크·I/O 오류 | 재시도 가능 | 다음 시도 예약 또는 마지막 시도 후 `FAILED` |
| 연결·요청 전체 timeout | 재시도 가능 | 다음 시도 예약 또는 마지막 시도 후 `FAILED` |
| HTTP `408`, `429` | 재시도 가능 | 다음 시도 예약 또는 마지막 시도 후 `FAILED` |
| HTTP `5xx` | 재시도 가능 | 다음 시도 예약 또는 마지막 시도 후 `FAILED` |
| 그 밖의 HTTP `4xx` | 영구 실패 | 즉시 `FAILED`, 추가 자동 재시도 없음 |
| HTTP `3xx` | 영구 실패 | redirect를 따르지 않고 즉시 `FAILED` |

- 연결 timeout: 2초
- 요청 전체 timeout: 5초
- lease: 30초
- 외부 응답 본문은 성공·실패 판정에 사용하거나 저장하지 않습니다.

### 시도 일정

| 시도 | 실행 시점 | 재시도 가능 오류가 발생하면 |
|---:|---|---|
| 1 | 주문 커밋과 동시에 전송 가능, 정상·backlog 없음 조건에서 2초 이내 요청 시작 | 1분 뒤 시도 2 예약 |
| 2 | 시도 1 실패 후 1분 | 5분 뒤 시도 3 예약 |
| 3 | 시도 2 실패 후 5분 | 30분 뒤 시도 4 예약 |
| 4 | 시도 3 실패 후 30분 | `FAILED` 전환 |

자동 재시도 상태 전이의 논리적 시도 횟수는 최초 1회를 포함해 4회입니다. 어느 시도에서든 재시도하지 않는 `4xx`가 오면 즉시 `FAILED`가 됩니다. lease 만료와 응답 유실로 발생하는 at-least-once 중복 HTTP 요청은 이 논리적 상한 밖에서 추가될 수 있습니다.

현재 `claim_token`으로 결과 갱신에 성공한 모든 실패 시도는 재시도 가능 여부와 관계없이 `retry_count`를 1 증가시킵니다. lease 만료 뒤 이전 claim의 결과가 fencing으로 거절되면 상태와 횟수를 변경하지 않습니다. 따라서 첫 시도의 영구 `3xx`·`4xx` 실패는 `retry_count=1`, 네 번째 재시도 가능 실패는 `retry_count=4`로 `FAILED`에 저장됩니다.

### 상태와 보존

| 상태 | 의미 | 다음 동작 |
|---|---|---|
| `PENDING` | 최초 전송 또는 예약 시각을 기다림 | 전송 가능 시각에 워커가 선점 |
| `PROCESSING` | 특정 `claim_token`의 워커가 처리 중 | 성공·재시도·실패 갱신 또는 lease 만료 후 재선점 |
| `PUBLISHED` | 외부 API가 `2xx`로 수신 | 발행 완료 후 최소 30일 보존 뒤 삭제 가능 |
| `FAILED` | 자동 재시도 종료 또는 영구 `3xx`·`4xx` | 자동 삭제 없이 보존 |

상태별 필드 계약은 다음과 같습니다.

| 상태 | `next_retry_at` | claim 정보 | 마지막 오류 정보 | 완료 시각 |
|---|---|---|---|---|
| 최초 `PENDING` | 즉시 전송 가능 시각 | `NULL` | `NULL` | 모두 `NULL` |
| 재시도 `PENDING` | 다음 예약 시각 | `NULL` | 직전 실패를 보존 | 모두 `NULL` |
| `PROCESSING` | 선점 전 전송 가능 시각을 유지 | `locked_at`, `claim_token` 필수 | 이전 실패가 있으면 보존 | 모두 `NULL` |
| `PUBLISHED` | `NULL` | `NULL` | 모두 `NULL`로 정리 | `published_at`만 존재 |
| `FAILED` | `NULL` | `NULL` | `last_error_type` 필수, HTTP 응답이 있을 때만 `last_http_status` 존재 | `failed_at`만 존재 |

`FAILED`에는 응답 본문·stack trace를 저장하지 않습니다. 수동 재시도 API와 자동 정리 스케줄러는 현재 범위에 포함하지 않습니다.

## 11. 입력 검증 규칙

- `userId`는 signed `BIGINT` 범위의 0보다 큰 정수여야 하며 존재하지 않으면 `USER_NOT_FOUND`입니다.
- `menuId`는 signed `BIGINT` 범위의 0보다 큰 정수여야 하며 범위 안에 있지만 존재하지 않으면 `MENU_NOT_FOUND`입니다.
- `Idempotency-Key`는 UUID 문자열만 허용하고 표준 소문자 문자열로 저장합니다.
- 정의되지 않은 JSON 필드, 필수 필드의 누락·`null`, 문자열에서 숫자로의 자동 형 변환은 `INVALID_REQUEST`입니다.
- 충전 `amount`가 정수가 아니거나 0 이하이거나 signed `BIGINT` 범위 밖이면 `INVALID_CHARGE_AMOUNT`입니다.
- 사용자 부재, 헤더·JSON·필드·충전 금액 검증 실패는 멱등 결과로 저장하지 않습니다.
