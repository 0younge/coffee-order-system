# 0012. JWT 구현에 Spring Security 사용

- 상태: 승인됨
- 날짜: 2026-07-13
- 결정자: 프로젝트 소유자

## 맥락

[ADR 0008](./0008-use-stateless-jwt-access-token-authentication.md)은 HS256 JWT Access Token 계약을 확정했지만 토큰 발급·검증 구현체는 정하지 않았다. JWT 서명, 파싱과 검증을 직접 구현하면 보안 경계와 예외 처리가 불필요하게 늘어난다.

## 고려한 대안

- JWT 직접 구현 — 새 라이브러리가 필요 없음 / 서명·파싱·검증 오류를 애플리케이션이 직접 책임져야 함
- 별도 JWT 라이브러리 직접 조합 — 선택 폭이 넓음 / Spring Security 인증 흐름과 예외 처리를 별도로 연결해야 함
- Spring Security OAuth2 Resource Server와 Nimbus — 표준 Bearer 인증 흐름을 재사용함 / Spring 관리 의존성이 추가됨

## 결정

`spring-boot-starter-oauth2-resource-server`를 추가하고 Spring Security의 `BCryptPasswordEncoder`, `NimbusJwtEncoder`, `NimbusJwtDecoder`를 사용한다. 애플리케이션은 로그인 성공 시 HS256 Access Token을 발급하고 Resource Server 인증 필터가 보호 API의 Bearer Token을 검증한다.

JWT 서명 키는 Base64로 주입한 값을 디코딩했을 때 최소 32바이트여야 하며 기본값을 두지 않는다. 토큰 유효기간은 1,800초, issuer는 `coffee-order-system`, 만료 허용 오차는 0초다. `sub`, `iat`, `exp`, `iss`가 없거나 형식이 잘못되면 인증을 거절하며 토큰 검증을 위해 사용자 DB를 매번 조회하지 않는다.

## 결과

- JWT 암호 처리와 Bearer 인증을 검증된 Spring Security 구성에 맡긴다.
- 애플리케이션은 프로젝트 고유 claim과 공개 오류 매핑에 집중한다.
- Resource Server와 Nimbus 의존성이 추가되며 Spring Security 업그레이드 시 호환성을 함께 검증해야 한다.
