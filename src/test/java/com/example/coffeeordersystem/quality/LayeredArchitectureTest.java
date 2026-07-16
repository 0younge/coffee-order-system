package com.example.coffeeordersystem.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
  private static final Set<String> INTERNAL_LAYERS = Set.of("api", "domain", "infrastructure");
  private static final Set<String> EXPLICIT_CONSTRUCTOR_COMPONENTS =
      Set.of(
          "DatabaseContentionMetrics.java",
          "OutboxHttpSender.java",
          "OutboxMetrics.java",
          "OutboxWorker.java");
  private static final Pattern FEATURE_IMPORT =
      Pattern.compile(
          "(?m)^import com\\.example\\.coffeeordersystem\\.([a-z]+)(?:\\.([A-Za-z0-9_]+))?.*;$");
  private static final Pattern SPRING_COMPONENT =
      Pattern.compile(
          "(?m)^\\s*@(RestController|RestControllerAdvice|Component|Service|Repository|Configuration)\\b");

  @Test
  @DisplayName("QT-ARCH-001 현재 기능 경계와 점진적 계층 의존 방향을 지킨다")
  void keepsCurrentFeatureAndLayerDependenciesAcyclic() throws IOException {
    List<SourceFile> sources = javaSources();
    Map<String, Set<String>> dependencies = featureDependencies(sources);

    for (SourceFile source : sources) {
      if (source.contents().contains("@RestController")) {
        assertFalse(
            Pattern.compile("(?m)^import .*Repository;$").matcher(source.contents()).find(),
            source.path() + " Controller는 Repository를 직접 참조할 수 없습니다.");
        assertFalse(
            source.contents().contains("JdbcTemplate"),
            source.path() + " Controller는 JdbcTemplate을 직접 참조할 수 없습니다.");
      }
      verifyDomainIndependence(source);
      verifyCrossFeatureBoundary(source);
    }

    assertFalse(hasFeatureCycle(dependencies), "기능 간 순환 의존이 없어야 합니다: " + dependencies);
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
      assertFalse(source.contents().contains("@Autowired"), source.path() + " 필드 주입을 금지합니다.");
      assertFalse(
          source.contents().contains("LoggerFactory"),
          source.path() + " 수동 LoggerFactory 보일러플레이트를 금지합니다.");
      for (String forbidden : List.of("@SneakyThrows", "@Synchronized", "@Cleanup")) {
        assertFalse(
            source.contents().contains(forbidden),
            source.path() + " 승인되지 않은 Lombok annotation을 금지합니다: " + forbidden);
      }

      if (SPRING_COMPONENT.matcher(source.contents()).find()
          && source.contents().contains("private final ")) {
        String fileName = source.path().getFileName().toString();
        if (EXPLICIT_CONSTRUCTOR_COMPONENTS.contains(fileName)) {
          String className = fileName.substring(0, fileName.length() - ".java".length());
          assertTrue(
              source.contents().contains(className + "("),
              source.path() + " 예외 컴포넌트는 의미 있는 명시 생성자를 유지해야 합니다.");
        } else {
          assertTrue(
              source.contents().contains("@RequiredArgsConstructor"),
              source.path() + " final 의존성은 Lombok 생성자 주입을 사용해야 합니다.");
        }
      }

      if (source.contents().contains("@Entity")) {
        assertTrue(
            source.contents().contains("@NoArgsConstructor(access = AccessLevel.PROTECTED)"),
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
              Pattern.compile("(?m)^\\s*" + Pattern.quote(forbidden) + "\\b")
                  .matcher(source.contents())
                  .find(),
              source.path() + " Entity에는 " + forbidden + "를 사용할 수 없습니다.");
        }
      }
    }
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
            "org.springframework.jdbc",
            "tools.jackson",
            "com.fasterxml.jackson")) {
      assertFalse(
          source.contents().contains("import ") && source.contents().contains(forbidden),
          source.path() + " domain은 " + forbidden + "에 의존할 수 없습니다.");
    }
  }

  private void verifyCrossFeatureBoundary(SourceFile source) {
    String sourceFeature = featureOf(source.path());
    if (sourceFeature == null) {
      return;
    }
    Matcher imports = FEATURE_IMPORT.matcher(source.contents());
    while (imports.find()) {
      String targetFeature = imports.group(1);
      String targetLayer = imports.group(2);
      if (!sourceFeature.equals(targetFeature)
          && FEATURES.contains(targetFeature)
          && INTERNAL_LAYERS.contains(targetLayer)) {
        fail(source.path() + " 다른 기능의 " + targetLayer + "를 직접 참조할 수 없습니다: " + imports.group());
      }
    }
  }

  private Map<String, Set<String>> featureDependencies(List<SourceFile> sources) {
    Map<String, Set<String>> dependencies = new HashMap<>();
    FEATURES.forEach(feature -> dependencies.put(feature, new HashSet<>()));
    for (SourceFile source : sources) {
      String sourceFeature = featureOf(source.path());
      if (sourceFeature == null) {
        continue;
      }
      Matcher imports = FEATURE_IMPORT.matcher(source.contents());
      while (imports.find()) {
        String targetFeature = imports.group(1);
        if (FEATURES.contains(targetFeature) && !sourceFeature.equals(targetFeature)) {
          dependencies.get(sourceFeature).add(targetFeature);
        }
      }
    }
    return dependencies;
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

  private record SourceFile(Path path, String contents) {}
}
