package com.example.coffeeordersystem.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LayeredArchitectureTest {

  private static final Path MAIN_SOURCE = Path.of("src/main/java/com/example/coffeeordersystem");
  private static final Set<String> FEATURES =
      Set.of("idempotency", "menu", "order", "outbox", "point");
  private static final Set<String> FEATURE_LAYERS =
      Set.of("api", "application", "domain", "infrastructure");
  private static final Set<String> EXPLICIT_CONSTRUCTOR_COMPONENTS =
      Set.of(
          "common/observability/DatabaseContentionMetrics.java",
          "outbox/infrastructure/OutboxHttpSender.java",
          "outbox/infrastructure/OutboxMetrics.java");
  private static final Pattern FEATURE_REFERENCE =
      Pattern.compile(
          "(?m)(?:^import(?: static)?\\s+)?com\\.example\\.coffeeordersystem\\.([a-z]+)"
              + "(?:\\.([A-Za-z0-9_]+))?[A-Za-z0-9_.$]*");
  private static final Pattern FIELD_INJECTION =
      Pattern.compile(
          "(?s)@(?:[A-Za-z_$][\\w$]*\\.)*(?:Autowired|Value|Resource|Inject)\\b"
              + "(?:\\s*\\([^;{}]*\\))?\\s*"
              + "(?:@(?:[A-Za-z_$][\\w$]*\\.)*[A-Za-z_$][\\w$]*"
              + "(?:\\s*\\([^;{}]*\\))?\\s*)*"
              + "(?:(?:public|protected|private|static|final|transient|volatile)\\s+)*"
              + "[\\w.$<>?, \\[\\]]+\\s+\\w+\\s*(?:=[^;{}]*)?;");
  private static final Pattern PROTECTED_NO_ARGS_CONSTRUCTOR =
      Pattern.compile(
          "@(?:lombok\\.)?NoArgsConstructor\\s*\\(\\s*access\\s*=\\s*"
              + "(?:lombok\\.)?AccessLevel\\.PROTECTED\\s*\\)");

  @Test
  @DisplayName("QT-ARCH-001 기능 경계와 완성된 계층 의존 방향을 지킨다")
  void keepsCurrentFeatureAndLayerDependenciesAcyclic() throws IOException {
    List<SourceFile> sources = javaSources();
    Map<String, Set<String>> dependencies = featureDependencies(sources);
    Set<String> persistenceTypes = persistenceTypeNames(sources);

    for (SourceFile source : sources) {
      verifyProjectSourceLocation(source);
      verifyControllerDoesNotUsePersistence(source, persistenceTypes);
      verifyControllerUsesOwnFacadeOnly(source);
      verifyApiIndependence(source);
      verifyApplicationIndependence(source);
      verifyDomainIndependence(source);
      verifyCrossFeatureBoundary(source);
    }

    assertFalse(hasFeatureCycle(dependencies), "기능 간 순환 의존이 없어야 합니다: " + dependencies);
    assertTrue(
        Files.exists(MAIN_SOURCE.resolve("idempotency/application/IdempotencyFacade.java")),
        "Idempotency는 공개 Application Facade를 제공해야 합니다.");
    assertFalse(
        Files.exists(MAIN_SOURCE.resolve("idempotency/IdempotencyService.java")),
        "IdempotencyFacade 뒤에 기존 Service 위임 계층을 남길 수 없습니다.");
    assertTrue(
        Files.exists(MAIN_SOURCE.resolve("menu/api/MenuController.java")),
        "Menu Controller는 API 계층에 있어야 합니다.");
    assertTrue(
        Files.exists(MAIN_SOURCE.resolve("menu/application/MenuQueryFacade.java")),
        "Menu는 공개 Application Facade를 제공해야 합니다.");
    assertFalse(
        sources.stream()
            .filter(source -> normalized(source.path()).contains("/menu/"))
            .map(source -> source.path().getFileName().toString())
            .anyMatch(
                name -> name.equals("MenuService.java") || name.equals("PopularMenuService.java")),
        "MenuQueryFacade 뒤에 기존 Service 위임 계층을 남길 수 없습니다.");
    String menuController = Files.readString(MAIN_SOURCE.resolve("menu/api/MenuController.java"));
    verifyMenuControllerFacadeOnly(
        new SourceFile(MAIN_SOURCE.resolve("menu/api/MenuController.java"), menuController));
    assertTrue(
        Files.exists(MAIN_SOURCE.resolve("point/api/PointController.java")),
        "Point Controller는 API 계층에 있어야 합니다.");
    assertTrue(
        Files.exists(MAIN_SOURCE.resolve("point/application/PointFacade.java")),
        "Point 충전은 공개 Application Facade를 제공해야 합니다.");
    assertTrue(
        Files.exists(MAIN_SOURCE.resolve("point/application/PointPaymentFacade.java")),
        "Point 결제는 주문이 사용할 공개 Application Facade를 제공해야 합니다.");
    String chargeCommand =
        Files.readString(MAIN_SOURCE.resolve("point/application/ChargeCommand.java"));
    assertFalse(
        chargeCommand.contains("public record ChargeCommand"),
        "ChargeCommand는 공개 canonical constructor로 검증을 우회할 수 없습니다.");
    assertTrue(
        chargeCommand.contains("private ChargeCommand("),
        "ChargeCommand 생성은 검증된 정적 팩터리로 제한해야 합니다.");
    assertFalse(
        sources.stream()
            .filter(source -> normalized(source.path()).contains("/point/"))
            .map(source -> source.path().getFileName().toString())
            .anyMatch(
                name ->
                    name.equals("PointService.java") || name.equals("PointPaymentService.java")),
        "Point Facade 뒤에 기존 Service 위임 계층을 남길 수 없습니다.");
    String pointController =
        Files.readString(MAIN_SOURCE.resolve("point/api/PointController.java"));
    verifyPointControllerFacadeOnly(
        new SourceFile(MAIN_SOURCE.resolve("point/api/PointController.java"), pointController));
    assertTrue(
        Files.exists(MAIN_SOURCE.resolve("order/api/OrderController.java")),
        "Order Controller는 API 계층에 있어야 합니다.");
    assertTrue(
        Files.exists(MAIN_SOURCE.resolve("order/application/OrderFacade.java")),
        "Order는 공개 Application Facade를 제공해야 합니다.");
    assertTrue(
        Files.exists(MAIN_SOURCE.resolve("order/domain/Order.java")),
        "Order Entity는 Domain 계층에 있어야 합니다.");
    assertTrue(
        Files.exists(MAIN_SOURCE.resolve("order/infrastructure/OrderRepository.java")),
        "Order Repository는 Infrastructure 계층에 있어야 합니다.");
    assertFalse(
        sources.stream()
            .filter(source -> normalized(source.path()).contains("/order/"))
            .map(source -> source.path().getFileName().toString())
            .anyMatch(name -> name.equals("OrderService.java")),
        "OrderFacade 뒤에 기존 Service 위임 계층을 남길 수 없습니다.");
    String orderController =
        Files.readString(MAIN_SOURCE.resolve("order/api/OrderController.java"));
    verifyOrderControllerFacadeOnly(
        new SourceFile(MAIN_SOURCE.resolve("order/api/OrderController.java"), orderController));
    String orderCommand =
        Files.readString(MAIN_SOURCE.resolve("order/application/OrderCommand.java"));
    assertFalse(
        orderCommand.contains("public record OrderCommand"),
        "OrderCommand는 공개 canonical constructor로 검증을 우회할 수 없습니다.");
    assertTrue(
        orderCommand.contains("private OrderCommand("), "OrderCommand 생성은 검증된 정적 팩터리로 제한해야 합니다.");
    String orderFacade =
        Files.readString(MAIN_SOURCE.resolve("order/application/OrderFacade.java"));
    assertTrue(orderFacade.contains("@Transactional"), "OrderFacade가 주문 트랜잭션 경계를 소유해야 합니다.");
    assertTrue(
        orderFacade.contains("menu.application.MenuQueryFacade")
            && orderFacade.contains("menu.application.MenuSnapshot"),
        "Order는 Menu Application Facade와 Snapshot 계약만 사용해야 합니다.");
    assertFalse(orderFacade.contains("MenuResponse"), "Order는 Menu API 응답 DTO를 사용할 수 없습니다.");
    assertTrue(
        orderFacade.contains("point.application.PointPaymentFacade")
            && orderFacade.contains("point.application.LockedPointBalance"),
        "Order는 Point Application 결제 계약만 사용해야 합니다.");
    assertFalse(
        orderFacade.contains("point.api.")
            || orderFacade.contains("point.domain.")
            || orderFacade.contains("point.infrastructure."),
        "Order는 Point API, Domain, Infrastructure를 직접 참조할 수 없습니다.");
    assertTrue(
        Files.exists(MAIN_SOURCE.resolve("outbox/application/OutboxEventAppender.java")),
        "Outbox는 주문 이벤트 기록용 공개 Application 계약을 제공해야 합니다.");
    assertTrue(
        Files.exists(MAIN_SOURCE.resolve("outbox/infrastructure/JdbcOutboxEventAppender.java")),
        "Outbox 이벤트 JSON/JDBC 저장은 Infrastructure에 있어야 합니다.");
    String outboxEventAppender =
        Files.readString(MAIN_SOURCE.resolve("outbox/infrastructure/JdbcOutboxEventAppender.java"));
    assertTrue(
        outboxEventAppender.contains("Propagation.MANDATORY"),
        "Outbox 이벤트 기록은 기존 주문 트랜잭션에 반드시 참여해야 합니다.");
    assertFalse(
        sources.stream()
            .map(source -> source.path().getFileName().toString())
            .anyMatch(name -> name.equals("OutboxEventWriter.java")),
        "기존 OutboxEventWriter를 공개 구현으로 남길 수 없습니다.");
    assertTrue(
        orderFacade.contains("outbox.application.OutboxEventAppender"),
        "Order는 Outbox Application 기록 계약만 사용해야 합니다.");
    assertFalse(
        orderFacade.contains("outbox.api.")
            || orderFacade.contains("outbox.domain.")
            || orderFacade.contains("outbox.infrastructure."),
        "Order는 Outbox API, Domain, Infrastructure를 직접 참조할 수 없습니다.");
    assertTrue(
        Files.exists(MAIN_SOURCE.resolve("outbox/application/OutboxDeliveryFacade.java")),
        "Outbox 전달은 Application Facade가 조정해야 합니다.");
    assertTrue(
        Files.exists(MAIN_SOURCE.resolve("outbox/domain/OutboxRetryPolicy.java")),
        "Outbox 재시도 규칙은 Domain 계층에 있어야 합니다.");
    assertTrue(
        Files.exists(MAIN_SOURCE.resolve("outbox/infrastructure/OutboxStore.java")),
        "Outbox SQL 저장소는 Infrastructure 계층에 있어야 합니다.");
    assertTrue(
        Files.exists(MAIN_SOURCE.resolve("outbox/infrastructure/OutboxHttpSender.java")),
        "Outbox HTTP Client는 Infrastructure 계층에 있어야 합니다.");
    assertTrue(
        Files.exists(MAIN_SOURCE.resolve("outbox/infrastructure/OutboxWorker.java")),
        "Outbox Worker는 Infrastructure scheduler adapter여야 합니다.");
    assertFalse(
        Files.exists(MAIN_SOURCE.resolve("outbox/OutboxWorker.java"))
            || Files.exists(MAIN_SOURCE.resolve("outbox/OutboxStore.java"))
            || Files.exists(MAIN_SOURCE.resolve("outbox/OutboxHttpSender.java")),
        "Outbox 전달 구현을 flat package에 남길 수 없습니다.");
    String outboxWorker =
        Files.readString(MAIN_SOURCE.resolve("outbox/infrastructure/OutboxWorker.java"));
    assertTrue(
        outboxWorker.contains("outboxDeliveryFacade.deliverDue(settings.batchSize())"),
        "OutboxWorker는 설정된 배치 크기로 Delivery Facade만 호출해야 합니다.");
    for (String forbidden :
        List.of(
            "OutboxStore",
            "OutboxHttpSender",
            "OutboxMetrics",
            "AtomicBoolean",
            "CompletableFuture",
            "Clock")) {
      assertFalse(
          outboxWorker.contains(forbidden),
          "OutboxWorker scheduler adapter가 전달 조정을 소유할 수 없습니다: " + forbidden);
    }
    String outboxDeliveryFacade =
        Files.readString(MAIN_SOURCE.resolve("outbox/application/OutboxDeliveryFacade.java"));
    assertFalse(
        outboxDeliveryFacade.contains("@Scheduled"),
        "OutboxDeliveryFacade는 scheduler annotation을 소유할 수 없습니다.");
    assertTrue(
        outboxDeliveryFacade.contains("AtomicBoolean")
            && outboxDeliveryFacade.contains("CompletableFuture.allOf")
            && outboxDeliveryFacade.contains("outboxStore.claim")
            && outboxDeliveryFacade.contains("outboxStore.publish")
            && outboxDeliveryFacade.contains("outboxStore.fail"),
        "OutboxDeliveryFacade가 단일 활성 배치의 claim·병렬 전송·결과 조정을 소유해야 합니다.");
    String outboxStore =
        Files.readString(MAIN_SOURCE.resolve("outbox/infrastructure/OutboxStore.java"));
    assertTrue(
        outboxStore.contains("FOR UPDATE SKIP LOCKED")
            && outboxStore.contains("Propagation.REQUIRES_NEW")
            && outboxStore.contains("Duration.ofSeconds(30)")
            && outboxStore.contains("claim_token"),
        "OutboxStore는 SKIP LOCKED·30초 lease·fencing·독립 상태 트랜잭션을 보존해야 합니다.");
  }

  @Test
  @DisplayName("QT-ARCH-001 Common은 승인된 공통 경계만 소유하고 기능에 의존하지 않는다")
  void keepsCommonBoundarySmallAndFeatureIndependent() throws IOException {
    Path commonSource = MAIN_SOURCE.resolve("common");
    Set<String> commonPackages;
    try (var paths = Files.list(commonSource)) {
      commonPackages =
          paths
              .filter(Files::isDirectory)
              .map(path -> path.getFileName().toString())
              .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    assertEquals(
        Set.of("api", "error", "observability"),
        commonPackages,
        "Common은 실제로 재사용되는 API·오류·관측성 경계만 가져야 합니다.");

    for (SourceFile source : javaSources()) {
      verifyCommonLocation(source);
      verifyCommonIndependence(source);
      if (normalized(source.path()).contains("/common/")) {
        assertFalse(
            source.path().getFileName().toString().startsWith("Base"),
            source.path() + " Common에 범용 Base 추상화를 둘 수 없습니다.");
      }
    }

    String globalExceptionHandler =
        Files.readString(commonSource.resolve("error/GlobalExceptionHandler.java"));
    assertTrue(
        hasAnnotation(globalExceptionHandler, "RestControllerAdvice"),
        "공통 오류는 하나의 HTTP 예외 변환 경계를 제공해야 합니다.");
    String requestLoggingFilter =
        Files.readString(commonSource.resolve("observability/RequestLoggingFilter.java"));
    assertFalse(
        requestLoggingFilter.contains("public class RequestLoggingFilter"),
        "HTTP 요청 로깅 구현은 Common 밖에 공개할 필요가 없습니다.");
  }

  @Test
  @DisplayName("QT-ARCH-001 정적 import와 저장소 역할 타입 우회를 탐지한다")
  void detectsStaticImportsAndPersistenceRoleTypes() {
    SourceFile staticImport =
        syntheticSource(
            "order/application/SyntheticOrder.java",
            "import static com.example.coffeeordersystem.point.domain.PointAccount.fixture;");
    assertThrows(AssertionError.class, () -> verifyCrossFeatureBoundary(staticImport));

    SourceFile qualifiedFeatureReference =
        syntheticSource(
            "order/application/QualifiedOrder.java",
            "class QualifiedOrder { "
                + "com.example.coffeeordersystem.point.domain.PointAccount account; }");
    assertThrows(AssertionError.class, () -> verifyCrossFeatureBoundary(qualifiedFeatureReference));

    SourceFile qualifiedDomainLeak =
        syntheticSource(
            "point/domain/QualifiedDomain.java",
            "class QualifiedDomain { org.springframework.jdbc.core.JdbcTemplate jdbc; }");
    assertThrows(AssertionError.class, () -> verifyDomainIndependence(qualifiedDomainLeak));
    SourceFile qualifiedHttpLeak =
        syntheticSource(
            "point/domain/QualifiedHttpDomain.java",
            "class QualifiedHttpDomain { org.springframework.http.ResponseEntity<?> response; }");
    assertThrows(AssertionError.class, () -> verifyDomainIndependence(qualifiedHttpLeak));
    SourceFile qualifiedApplicationJdbcLeak =
        syntheticSource(
            "idempotency/application/DirectJdbc.java",
            "class DirectJdbc { org.springframework.jdbc.core.JdbcTemplate jdbc; }");
    assertThrows(
        AssertionError.class, () -> verifyApplicationIndependence(qualifiedApplicationJdbcLeak));
    SourceFile commonFeatureLeak =
        syntheticSource(
            "common/api/SyntheticResponse.java",
            "import com.example.coffeeordersystem.order.application.OrderResult;");
    assertThrows(AssertionError.class, () -> verifyCommonIndependence(commonFeatureLeak));
    SourceFile commonRootFile =
        syntheticSource("common/SharedHelper.java", "class SharedHelper {}");
    assertThrows(AssertionError.class, () -> verifyCommonLocation(commonRootFile));
    SourceFile flatFeatureSource =
        syntheticSource("order/LegacyOrderService.java", "class LegacyOrderService {}");
    assertThrows(AssertionError.class, () -> verifyProjectSourceLocation(flatFeatureSource));
    SourceFile globalTechnicalSource =
        syntheticSource("service/GlobalService.java", "class GlobalService {}");
    assertThrows(AssertionError.class, () -> verifyProjectSourceLocation(globalTechnicalSource));
    SourceFile apiDomainLeak =
        syntheticSource(
            "menu/api/SyntheticResponse.java",
            "import com.example.coffeeordersystem.menu.domain.Menu;");
    assertThrows(AssertionError.class, () -> verifyApiIndependence(apiDomainLeak));
    SourceFile apiCrossFeatureApplicationLeak =
        syntheticSource(
            "menu/api/CrossFeatureController.java",
            "import com.example.coffeeordersystem.point.application.PointFacade;");
    assertThrows(AssertionError.class, () -> verifyApiIndependence(apiCrossFeatureApplicationLeak));
    SourceFile controllerWithServiceDependency =
        syntheticSource(
            "menu/api/SyntheticController.java",
            "@org.springframework.stereotype.Controller\n"
                + "class SyntheticController {\n"
                + "  private MenuQueryService menuQueryService;\n"
                + "}");
    assertThrows(
        AssertionError.class,
        () -> verifyControllerUsesOwnFacadeOnly(controllerWithServiceDependency));
    SourceFile controllerWithConstructorDependency =
        syntheticSource(
            "menu/api/ConstructorController.java",
            "import com.example.coffeeordersystem.menu.application.MenuQueryFacade;\n"
                + "@org.springframework.web.bind.annotation.RestController\n"
                + "class ConstructorController {\n"
                + "  private final MenuQueryFacade menuQueryFacade;\n"
                + "  ConstructorController(MenuQueryFacade facade, MenuQueryService service) {}\n"
                + "}");
    assertThrows(
        AssertionError.class,
        () -> verifyControllerUsesOwnFacadeOnly(controllerWithConstructorDependency));
    SourceFile controllerWithPackagePrivateService =
        syntheticSource(
            "menu/api/PackagePrivateController.java",
            "import com.example.coffeeordersystem.menu.application.MenuQueryFacade;\n"
                + "@org.springframework.web.bind.annotation.RestController\n"
                + "class PackagePrivateController {\n"
                + "  private final MenuQueryFacade menuQueryFacade;\n"
                + "  @Deprecated final com.example.coffeeordersystem.menu.application.MenuQueryService menuQueryService;\n"
                + "}");
    assertThrows(
        AssertionError.class,
        () -> verifyControllerUsesOwnFacadeOnly(controllerWithPackagePrivateService));
    SourceFile crossFeatureFlatLeak =
        syntheticSource(
            "order/application/LegacyPointCaller.java",
            "import com.example.coffeeordersystem.point.LegacyPointService;");
    assertThrows(AssertionError.class, () -> verifyCrossFeatureBoundary(crossFeatureFlatLeak));
    SourceFile menuControllerWithExtraDependency =
        syntheticSource(
            "menu/api/MenuController.java",
            "class MenuController {\n"
                + " private final MenuQueryFacade menuQueryFacade;\n"
                + " private final PointFacade pointFacade;\n}");
    assertThrows(
        AssertionError.class,
        () -> verifyMenuControllerFacadeOnly(menuControllerWithExtraDependency));
    SourceFile pointControllerWithExtraDependency =
        syntheticSource(
            "point/api/PointController.java",
            "class PointController {\n"
                + " private final PointFacade pointFacade;\n"
                + " private final PointAccountRepository pointAccountRepository;\n}");
    assertThrows(
        AssertionError.class,
        () -> verifyPointControllerFacadeOnly(pointControllerWithExtraDependency));
    SourceFile orderControllerWithExtraDependency =
        syntheticSource(
            "order/api/OrderController.java",
            "class OrderController {\n"
                + " private final OrderFacade orderFacade;\n"
                + " private final OrderRepository orderRepository;\n}");
    assertThrows(
        AssertionError.class,
        () -> verifyOrderControllerFacadeOnly(orderControllerWithExtraDependency));

    SourceFile store =
        syntheticSource(
            "order/infrastructure/OrderStore.java",
            "@org.springframework.stereotype.Repository class OrderStore {}");
    SourceFile controller =
        syntheticSource(
            "order/api/SyntheticController.java",
            "@org.springframework.web.bind.annotation.RestController "
                + "class SyntheticController { private final OrderStore store = null; }");
    Set<String> persistenceTypes = persistenceTypeNames(List.of(store, controller));
    assertThrows(
        AssertionError.class,
        () -> verifyControllerDoesNotUsePersistence(controller, persistenceTypes));
    SourceFile mvcControllerWithJdbc =
        syntheticSource(
            "menu/api/MvcController.java",
            "@org.springframework.stereotype.Controller class MvcController {"
                + " void query(org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {} }");
    assertThrows(
        AssertionError.class,
        () -> verifyControllerDoesNotUsePersistence(mvcControllerWithJdbc, persistenceTypes));
  }

  @Test
  @DisplayName("QT-LOMBOK-001 Spring 컴포넌트와 Entity가 제한된 Lombok 정책을 지킨다")
  void usesLombokOnlyForApprovedBoilerplate() throws IOException {
    String build = Files.readString(Path.of("build.gradle"));
    assertEquals(
        1,
        build
            .lines()
            .filter(line -> line.trim().equals("compileOnly 'org.projectlombok:lombok'"))
            .count());
    assertEquals(
        1,
        build
            .lines()
            .filter(line -> line.trim().equals("annotationProcessor 'org.projectlombok:lombok'"))
            .count());
    assertFalse(build.contains("implementation 'org.projectlombok:lombok'"));
    assertFalse(build.contains("runtimeOnly 'org.projectlombok:lombok'"));
    assertFalse(build.contains("testCompileOnly 'org.projectlombok:lombok'"));
    assertFalse(build.contains("testAnnotationProcessor 'org.projectlombok:lombok'"));

    for (SourceFile source : javaSources()) {
      assertFalse(
          FIELD_INJECTION.matcher(source.contents()).find(), source.path() + " 필드 주입을 금지합니다.");
      assertFalse(
          source.contents().contains("LoggerFactory"),
          source.path() + " 수동 LoggerFactory 보일러플레이트를 금지합니다.");
      for (String forbidden : List.of("@SneakyThrows", "@Synchronized", "@Cleanup")) {
        assertFalse(
            hasAnnotation(source.contents(), forbidden.substring(1)),
            source.path() + " 승인되지 않은 Lombok annotation을 금지합니다: " + forbidden);
      }

      if (hasAnyAnnotation(
              source.contents(),
              "RestController",
              "RestControllerAdvice",
              "Component",
              "Service",
              "Repository",
              "Configuration")
          && source.contents().contains("private final ")) {
        String sourcePath = relativeSourcePath(source.path());
        if (EXPLICIT_CONSTRUCTOR_COMPONENTS.contains(sourcePath)) {
          String fileName = source.path().getFileName().toString();
          String className = fileName.substring(0, fileName.length() - ".java".length());
          assertTrue(
              Pattern.compile(
                      "(?m)^\\s*(?:public|protected|private)?\\s*"
                          + Pattern.quote(className)
                          + "\\s*\\(")
                  .matcher(source.contents())
                  .find(),
              source.path() + " 예외 컴포넌트는 의미 있는 명시 생성자를 유지해야 합니다.");
        } else {
          assertTrue(
              hasAnnotation(source.contents(), "RequiredArgsConstructor"),
              source.path() + " final 의존성은 Lombok 생성자 주입을 사용해야 합니다.");
        }
      }

      if (hasAnnotation(source.contents(), "Entity")) {
        assertTrue(
            PROTECTED_NO_ARGS_CONSTRUCTOR.matcher(source.contents()).find(),
            source.path() + " JPA 기본 생성자는 protected여야 합니다.");
        for (String forbidden :
            List.of(
                "@Data",
                "@Setter",
                "@AllArgsConstructor",
                "@Builder",
                "@EqualsAndHashCode",
                "@ToString")) {
          assertFalse(
              hasAnnotation(source.contents(), forbidden.substring(1)),
              source.path() + " Entity에는 " + forbidden + "를 사용할 수 없습니다.");
        }
      }
    }
  }

  @Test
  @DisplayName("QT-LOMBOK-001 완전 수식 annotation과 필드 주입만 정확히 탐지한다")
  void normalizesQualifiedAnnotationsAndDetectsOnlyFieldInjection() {
    assertTrue(hasAnnotation("@lombok.Data class Sample {}", "Data"));
    assertTrue(hasAnnotation("@jakarta.persistence.Entity class Sample {}", "Entity"));
    assertTrue(
        FIELD_INJECTION
            .matcher("@jakarta.inject.Inject private SampleRepository repository;")
            .find());
    assertTrue(
        FIELD_INJECTION
            .matcher(
                "@Autowired @org.springframework.beans.factory.annotation.Qualifier(\"primary\") "
                    + "private SampleRepository repository;")
            .find());
    assertFalse(
        FIELD_INJECTION.matcher("@Autowired Sample(@Value(\"${sample}\") String value) {}").find());
  }

  private void verifyControllerDoesNotUsePersistence(
      SourceFile source, Set<String> persistenceTypes) {
    if (!hasAnyAnnotation(source.contents(), "RestController", "Controller")) {
      return;
    }
    assertFalse(
        source.contents().contains("JdbcTemplate"),
        source.path() + " Controller는 JdbcTemplate을 직접 참조할 수 없습니다.");
    for (String type : persistenceTypes) {
      assertFalse(
          Pattern.compile("\\b" + Pattern.quote(type) + "\\b").matcher(source.contents()).find(),
          source.path() + " Controller는 저장소 역할 타입 " + type + "을 직접 참조할 수 없습니다.");
    }
  }

  private void verifyControllerUsesOwnFacadeOnly(SourceFile source) {
    if (!hasAnyAnnotation(source.contents(), "RestController", "Controller")) {
      return;
    }
    String sourceFeature = featureOf(source.path());
    assertTrue(sourceFeature != null, source.path() + " Controller는 기능 API 경계에 있어야 합니다.");
    assertTrue(
        normalized(source.path()).contains("/" + sourceFeature + "/api/"),
        source.path() + " Controller는 자기 기능의 API 계층에 있어야 합니다.");
    List<String> dependencies = controllerInstanceFields(source.contents());
    assertEquals(1, dependencies.size(), source.path() + " Controller는 Facade 하나만 주입해야 합니다.");
    assertTrue(
        dependencies.get(0).startsWith("private final "),
        source.path() + " Controller 의존성은 private final 생성자 주입이어야 합니다.");
    Matcher dependency =
        Pattern.compile("private final ([A-Za-z_$][\\w$]*) [A-Za-z_$][\\w$]*;")
            .matcher(dependencies.get(0));
    assertTrue(dependency.find(), source.path() + " Controller 의존성 선언을 확인할 수 없습니다.");
    String facadeType = dependency.group(1);
    assertTrue(facadeType.endsWith("Facade"), source.path() + " Controller는 Facade만 호출해야 합니다.");
    assertTrue(
        source
            .contents()
            .contains(
                "import com.example.coffeeordersystem."
                    + sourceFeature
                    + ".application."
                    + facadeType
                    + ";"),
        source.path() + " Controller는 자기 기능의 Application Facade를 사용해야 합니다.");
    String className = source.path().getFileName().toString().replaceFirst("\\.java$", "");
    assertFalse(
        Pattern.compile(
                "(?m)^\\s*(?:public|protected|private)?\\s*" + Pattern.quote(className) + "\\s*\\(")
            .matcher(source.contents())
            .find(),
        source.path() + " Controller는 Lombok 생성자 주입 외 명시 생성자를 둘 수 없습니다.");
    for (String injection : List.of("Autowired", "Inject", "Resource")) {
      assertFalse(
          hasAnnotation(source.contents(), injection),
          source.path() + " Controller는 setter·method injection을 사용할 수 없습니다: " + injection);
    }
  }

  private List<String> controllerInstanceFields(String source) {
    return source
        .lines()
        .filter(
            line -> {
              String trimmed = line.trim();
              if (!trimmed.endsWith(";")
                  || Pattern.compile("(?:^|\\s)static\\s").matcher(trimmed).find()) {
                return false;
              }
              if (trimmed.matches("(?:private|protected|public)\\s+.*")) {
                return true;
              }
              return line.matches("^  \\S.*;$");
            })
        .map(String::trim)
        .toList();
  }

  private void verifyMenuControllerFacadeOnly(SourceFile source) {
    assertTrue(
        source.contents().contains("MenuQueryFacade"),
        "MenuController는 MenuQueryFacade를 사용해야 합니다.");
    assertEquals(
        1,
        source.contents().lines().filter(line -> line.contains("private final ")).count(),
        "MenuController의 주입 의존성은 MenuQueryFacade 하나여야 합니다.");
    assertTrue(
        source.contents().contains("private final MenuQueryFacade menuQueryFacade;"),
        "MenuController는 MenuQueryFacade를 생성자 주입해야 합니다.");
    assertFalse(
        source.contents().contains("com.example.coffeeordersystem.menu.domain.")
            || source.contents().contains("com.example.coffeeordersystem.menu.infrastructure."),
        "MenuController는 Menu Domain이나 Infrastructure를 직접 참조할 수 없습니다.");
  }

  private void verifyPointControllerFacadeOnly(SourceFile source) {
    assertTrue(
        source.contents().contains("PointFacade"), "PointController는 PointFacade를 사용해야 합니다.");
    assertEquals(
        1,
        source.contents().lines().filter(line -> line.contains("private final ")).count(),
        "PointController의 주입 의존성은 PointFacade 하나여야 합니다.");
    assertTrue(
        source.contents().contains("private final PointFacade pointFacade;"),
        "PointController는 PointFacade를 생성자 주입해야 합니다.");
    assertFalse(
        source.contents().contains("com.example.coffeeordersystem.point.domain.")
            || source.contents().contains("com.example.coffeeordersystem.point.infrastructure."),
        "PointController는 Point Domain이나 Infrastructure를 직접 참조할 수 없습니다.");
  }

  private void verifyOrderControllerFacadeOnly(SourceFile source) {
    assertTrue(
        source.contents().contains("OrderFacade"), "OrderController는 OrderFacade를 사용해야 합니다.");
    assertEquals(
        1,
        source.contents().lines().filter(line -> line.contains("private final ")).count(),
        "OrderController의 주입 의존성은 OrderFacade 하나여야 합니다.");
    assertTrue(
        source.contents().contains("private final OrderFacade orderFacade;"),
        "OrderController는 OrderFacade를 생성자 주입해야 합니다.");
    assertFalse(
        source.contents().contains("com.example.coffeeordersystem.order.domain.")
            || source.contents().contains("com.example.coffeeordersystem.order.infrastructure."),
        "OrderController는 Order Domain이나 Infrastructure를 직접 참조할 수 없습니다.");
  }

  private void verifyDomainIndependence(SourceFile source) {
    if (!normalized(source.path()).contains("/domain/")) {
      return;
    }
    for (String forbidden :
        List.of(
            ".api.",
            ".application.",
            ".infrastructure.",
            "org.springframework.web",
            "org.springframework.http",
            "org.springframework.jdbc",
            "java.net.http",
            "tools.jackson",
            "com.fasterxml.jackson")) {
      assertFalse(
          source.contents().contains(forbidden),
          source.path() + " domain은 " + forbidden + "에 의존할 수 없습니다.");
    }
  }

  private void verifyApiIndependence(SourceFile source) {
    if (!normalized(source.path()).contains("/api/")) {
      return;
    }
    String sourceFeature = featureOf(source.path());
    if (sourceFeature == null) {
      return;
    }
    for (String forbidden : List.of(".domain.", ".infrastructure.")) {
      assertFalse(
          source.contents().contains(forbidden),
          source.path() + " API는 자기 기능의 Application 경계만 사용해야 합니다: " + forbidden);
    }
    Matcher references = FEATURE_REFERENCE.matcher(source.contents());
    while (references.find()) {
      String targetFeature = references.group(1);
      if (FEATURES.contains(targetFeature) && !sourceFeature.equals(targetFeature)) {
        fail(source.path() + " API는 다른 기능을 직접 참조할 수 없습니다: " + references.group());
      }
    }
  }

  private void verifyApplicationIndependence(SourceFile source) {
    if (!normalized(source.path()).contains("/application/")) {
      return;
    }
    for (String forbidden :
        List.of(
            ".api.",
            "org.springframework.web",
            "org.springframework.http",
            "org.springframework.jdbc",
            "JdbcTemplate",
            "tools.jackson",
            "com.fasterxml.jackson")) {
      assertFalse(
          source.contents().contains(forbidden),
          source.path() + " application은 API 표현·DB 접근 기술 " + forbidden + "에 의존할 수 없습니다.");
    }
  }

  private void verifyCrossFeatureBoundary(SourceFile source) {
    String sourceFeature = featureOf(source.path());
    if (sourceFeature == null) {
      return;
    }
    Matcher references = FEATURE_REFERENCE.matcher(source.contents());
    while (references.find()) {
      String targetFeature = references.group(1);
      String targetLayer = references.group(2);
      if (!sourceFeature.equals(targetFeature)
          && FEATURES.contains(targetFeature)
          && !"application".equals(targetLayer)) {
        fail(source.path() + " 다른 기능은 Application 공개 계약으로만 참조해야 합니다: " + references.group());
      }
    }
  }

  private void verifyCommonIndependence(SourceFile source) {
    if (!normalized(source.path()).contains("/common/")) {
      return;
    }
    Matcher references = FEATURE_REFERENCE.matcher(source.contents());
    while (references.find()) {
      if (FEATURES.contains(references.group(1))) {
        fail(source.path() + " Common은 기능 코드에 의존할 수 없습니다: " + references.group());
      }
    }
  }

  private void verifyCommonLocation(SourceFile source) {
    String sourcePath = relativeSourcePath(source.path());
    if (!sourcePath.startsWith("common/")) {
      return;
    }
    assertTrue(
        sourcePath.matches("common/(api|error|observability)/[^/]+\\.java"),
        source.path() + " Common Java 소스는 승인된 API·오류·관측성 경계에 있어야 합니다.");
  }

  private void verifyProjectSourceLocation(SourceFile source) {
    Path relative = MAIN_SOURCE.relativize(source.path());
    if (relative.getNameCount() == 1) {
      assertEquals(
          "CoffeeOrderSystemApplication.java",
          relative.toString(),
          source.path() + " 최상위에는 애플리케이션 시작점만 둘 수 있습니다.");
      return;
    }
    String topLevel = relative.getName(0).toString();
    if (FEATURES.contains(topLevel)) {
      assertTrue(
          relative.getNameCount() >= 3 && FEATURE_LAYERS.contains(relative.getName(1).toString()),
          source.path() + " 기능 소스는 API·Application·Domain·Infrastructure 경계에 있어야 합니다.");
      if (relative.getName(1).toString().equals("api")) {
        assertTrue(
            Set.of("menu", "point", "order").contains(topLevel),
            source.path() + " HTTP API는 승인된 Menu·Point·Order 기능만 소유할 수 있습니다.");
      }
      return;
    }
    if (topLevel.equals("common")) {
      verifyCommonLocation(source);
      return;
    }
    if (topLevel.equals("config")) {
      assertEquals(
          "config/TimeConfiguration.java",
          relativeSourcePath(source.path()),
          source.path() + " 최상위 config 예외는 공용 UTC Clock 설정만 허용합니다.");
      return;
    }
    fail(source.path() + " 전역 기술 계층이나 승인되지 않은 최상위 패키지를 둘 수 없습니다.");
  }

  private Map<String, Set<String>> featureDependencies(List<SourceFile> sources) {
    Map<String, Set<String>> dependencies = new HashMap<>();
    FEATURES.forEach(feature -> dependencies.put(feature, new HashSet<>()));
    for (SourceFile source : sources) {
      String sourceFeature = featureOf(source.path());
      if (sourceFeature == null) {
        continue;
      }
      Matcher references = FEATURE_REFERENCE.matcher(source.contents());
      while (references.find()) {
        String targetFeature = references.group(1);
        if (FEATURES.contains(targetFeature) && !sourceFeature.equals(targetFeature)) {
          dependencies.get(sourceFeature).add(targetFeature);
        }
      }
    }
    return dependencies;
  }

  private Set<String> persistenceTypeNames(List<SourceFile> sources) {
    Set<String> names = new HashSet<>();
    for (SourceFile source : sources) {
      if (hasAnnotation(source.contents(), "Repository")
          || source.contents().contains("extends JpaRepository")
          || source.contents().contains("extends CrudRepository")
          || source.contents().contains("JdbcTemplate")) {
        names.add(source.path().getFileName().toString().replaceFirst("\\.java$", ""));
      }
    }
    return Set.copyOf(names);
  }

  private boolean hasFeatureCycle(Map<String, Set<String>> dependencies) {
    Set<String> visited = new HashSet<>();
    Set<String> visiting = new HashSet<>();
    for (String feature : FEATURES) {
      if (hasFeatureCycle(feature, dependencies, visiting, visited)) {
        return true;
      }
    }
    return false;
  }

  private boolean hasFeatureCycle(
      String feature,
      Map<String, Set<String>> dependencies,
      Set<String> visiting,
      Set<String> visited) {
    if (visiting.contains(feature)) {
      return true;
    }
    if (!visited.add(feature)) {
      return false;
    }
    visiting.add(feature);
    for (String dependency : dependencies.get(feature)) {
      if (hasFeatureCycle(dependency, dependencies, visiting, visited)) {
        return true;
      }
    }
    visiting.remove(feature);
    return false;
  }

  private String featureOf(Path path) {
    Path relative = MAIN_SOURCE.relativize(path);
    if (relative.getNameCount() == 0) {
      return null;
    }
    String candidate = relative.getName(0).toString();
    return FEATURES.contains(candidate) ? candidate : null;
  }

  private List<SourceFile> javaSources() throws IOException {
    List<SourceFile> sources = new ArrayList<>();
    try (var paths = Files.walk(MAIN_SOURCE)) {
      for (Path path : paths.filter(file -> file.toString().endsWith(".java")).toList()) {
        sources.add(new SourceFile(path, Files.readString(path)));
      }
    }
    return List.copyOf(sources);
  }

  private String normalized(Path path) {
    return "/" + path.toString().replace('\\', '/') + "/";
  }

  private String relativeSourcePath(Path path) {
    return MAIN_SOURCE.relativize(path).toString().replace('\\', '/');
  }

  private boolean hasAnyAnnotation(String source, String... simpleNames) {
    for (String simpleName : simpleNames) {
      if (hasAnnotation(source, simpleName)) {
        return true;
      }
    }
    return false;
  }

  private boolean hasAnnotation(String source, String simpleName) {
    return Pattern.compile(
            "(?m)^\\s*@(?:[A-Za-z_$][\\w$]*\\.)*" + Pattern.quote(simpleName) + "\\b")
        .matcher(source)
        .find();
  }

  private SourceFile syntheticSource(String relativePath, String contents) {
    return new SourceFile(MAIN_SOURCE.resolve(relativePath), contents);
  }

  private record SourceFile(Path path, String contents) {}
}
