# 0027. Lombok을 제한적으로 사용

- 상태: 승인됨
- 날짜: 2026-07-16
- 결정자: 프로젝트 소유자

## 맥락

기능 우선 계층형 구조로 Spring 컴포넌트가 분리되면 final 의존성 생성자와 로거 선언의 반복이 늘어난다. 명시적 생성자 주입과 도메인 불변식을 유지하면서 기계적 보일러플레이트를 줄일 방법이 필요하다. JPA Entity에 무분별한 코드 생성을 적용하면 상태 변경 경로와 식별자 의미가 흐려질 수 있으므로 사용 범위를 함께 정해야 한다.

## 고려한 대안

- 명시적 생성자와 Logger를 계속 작성 — 생성 코드와 도구 의존이 없음 / 반복 코드가 많고 구조 이동에서 기계적 수정 범위가 커짐
- Java record로 컴포넌트와 Entity를 전환 — 짧고 불변 데이터 표현에 적합 / Spring·JPA 수명주기와 행위가 있는 Entity에 맞지 않음
- Lombok의 `@Data`, `@Builder` 등을 폭넓게 사용 — 작성량을 크게 줄임 / Entity 불변식과 접근 범위를 약화하고 생성된 행위를 숨김
- 생성자·로거·JPA 기본 생성자와 필요한 Getter에만 Lombok 사용 — 짧고 일관된 생성자 주입 / annotation processing과 IDE 지원에 의존하고 생성 코드가 소스에 직접 보이지 않음

## 결정

Lombok을 이번 리팩토링에서 유일하게 허용하는 신규 의존성으로 추가한다. Spring Boot dependency management의 버전을 사용하고 Gradle에는 `compileOnly`와 `annotationProcessor`로만 선언한다. 테스트 소스가 Lombok annotation을 사용하지 않으면 테스트용 configuration은 추가하지 않는다.

- final 의존성을 생성자 주입하는 Spring Bean에는 `@RequiredArgsConstructor`를 적극 사용한다. 필드 주입과 `@Autowired` 필드 주입은 사용하지 않는다.
- 직접 선언한 LoggerFactory·Logger 필드는 로그 레벨·메시지·구조를 보존하며 `@Slf4j`로 대체한다.
- 안전하게 대체할 수 있는 JPA Entity의 보호된 기본 생성자에는 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`를 사용한다.
- 외부 호출자가 실제로 읽어야 하는 Entity·Domain 필드에만 선택적으로 `@Getter`를 사용한다. 이름 있는 도메인 메서드가 의미를 더 잘 표현하면 유지한다.
- Entity의 비즈니스 생성자는 private 또는 package-private로 유지하고 이름 있는 정적 팩터리와 `charge`, `pay` 같은 상태 전이 메서드가 불변식을 소유한다.
- Entity의 `@Data`, 클래스 전체 `@Setter`, 공개 `@AllArgsConstructor`, 무분별한 `@Builder`, `@EqualsAndHashCode`, `@ToString`을 금지한다. `@SneakyThrows`, `@Synchronized`, `@Cleanup`과 record의 불필요한 Lombok도 사용하지 않는다.

구조 테스트는 Lombok의 Gradle scope, Spring 컴포넌트 생성자 주입, Entity 금지 annotation을 검증한다.

## 결과

- 명시적 생성자보다 코드가 짧아지고 Spring Bean의 final 의존성 생성자 주입 방식이 일관된다.
- annotation processing과 IDE의 Lombok 지원이 컴파일·탐색 경험의 전제 조건이 된다.
- 생성된 생성자와 logger 필드가 소스에 직접 보이지 않아 annotation 의미를 알아야 한다.
- Entity에서는 JPA 기본 생성자와 읽기 접근만 제한적으로 생성하므로 정적 팩터리·도메인 행위·불변식을 계속 명시적으로 확인할 수 있다.
- Lombok을 제거할 때 annotation이 생성하던 생성자, 보호된 기본 생성자, Getter와 Logger를 명시적 코드로 복원하면 동작을 유지할 수 있다.
