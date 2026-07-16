package com.example.coffeeordersystem.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TraceabilityTest {

  private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[[^]]+]\\(([^)]+)\\)");
  private static final Pattern REQUIREMENT_ID = Pattern.compile("(?:FR|NFR)-[0-9]{2}");
  private static final Pattern POLICY_ID = Pattern.compile("POL-[A-Z]+-[0-9]{2}");
  private static final Pattern TEST_ID = Pattern.compile("[A-Z]{2,4}-[A-Z]+-[0-9]{3}");
  private static final Pattern DISPLAY_NAME = Pattern.compile("@DisplayName\\(\"([^\"]+)\"\\)");
  private static final Pattern TEST_METHOD =
      Pattern.compile(
          "(?m)((?:^[ \\t]*@[^\\r\\n]+\\R)+)^[ \\t]*(?:public |protected |private )?void \\w+\\s*\\(");

  @Test
  @DisplayName("QT-TRACE-001 README·요구사항·ADR·테스트 ID의 참조가 끊기지 않는다")
  void keepsDocumentationAndTestsTraceable() throws IOException {
    String build = Files.readString(Path.of("build.gradle"));
    String prd = Files.readString(Path.of("docs/prd.md"));
    String testStrategy = Files.readString(Path.of("docs/test-strategy.md"));
    String traceability = Files.readString(Path.of("docs/requirements-traceability.md"));

    verifyAllLocalLinks();
    assertEquals(requirementHeadings(prd), requirementRows(traceability));
    verifyCompletedScopeStatuses(traceability);
    assertEquals(adrFiles(), adrLinks(traceability));

    Set<String> documentedTestIds = findIds(testStrategy, TEST_ID);
    Set<String> implementedTestIds = implementedTestIds();
    Set<String> expectedImplemented = new HashSet<>(documentedTestIds);
    expectedImplemented.removeIf(id -> id.startsWith("PT-"));
    testStrategy
        .lines()
        .filter(line -> line.contains("예약"))
        .map(line -> findIds(line, TEST_ID))
        .forEach(expectedImplemented::removeAll);
    assertEquals(expectedImplemented, implementedTestIds);
    assertTrue(
        implementedTestIds.stream().allMatch(traceability::contains),
        "구현된 모든 테스트 ID가 요구사항 추적표에 연결되어야 합니다.");
    assertTrue(build.contains("inputs.files('README.md', fileTree('docs')"));
  }

  private void verifyCompletedScopeStatuses(String traceability) {
    traceability
        .lines()
        .filter(line -> line.matches("^\\| \\[(?:FR|NFR)-[0-9]{2}].*"))
        .forEach(line -> assertTrue(line.contains("| 검증됨 |"), "현재 범위 요구사항이 검증됨 상태여야 합니다: " + line));

    Set<String> expectedPolicies =
        Set.of(
            "POL-PLATFORM-01",
            "POL-IDEM-01",
            "POL-IDEM-02",
            "POL-POINT-01",
            "POL-OUTBOX-01",
            "POL-OUTBOX-02",
            "POL-OUTBOX-03",
            "POL-OUTBOX-04",
            "POL-OUTBOX-05",
            "POL-OUTBOX-06",
            "POL-OUTBOX-07",
            "POL-USER-01",
            "POL-OBS-01",
            "POL-POPULAR-01",
            "POL-PERF-01",
            "POL-RUN-01",
            "POL-TEST-01",
            "POL-SCHEMA-01",
            "POL-DEPS-01",
            "POL-FORMAT-01");
    List<String> policyRows =
        traceability.lines().filter(line -> line.matches("^\\| `POL-[A-Z]+-[0-9]{2}`.*")).toList();
    assertEquals(
        expectedPolicies,
        policyRows.stream()
            .map(POLICY_ID::matcher)
            .filter(Matcher::find)
            .map(Matcher::group)
            .collect(Collectors.toUnmodifiableSet()));

    Set<String> excludedPolicies = Set.of("POL-IDEM-02", "POL-OUTBOX-04");
    policyRows.stream()
        .forEach(
            line -> {
              Matcher policyId = POLICY_ID.matcher(line);
              assertTrue(policyId.find(), "정책 ID를 찾을 수 없습니다: " + line);
              String expectedStatus =
                  excludedPolicies.contains(policyId.group()) ? "| 현재 범위 제외 |" : "| 검증됨 |";
              assertTrue(line.contains(expectedStatus), "현재 범위 정책 상태가 완료 또는 명시적 제외여야 합니다: " + line);
            });
  }

  private void verifyAllLocalLinks() throws IOException {
    verifyLocalLinks(Path.of("README.md"), Files.readString(Path.of("README.md")));
    try (var paths = Files.walk(Path.of("docs"))) {
      for (Path document : paths.filter(path -> path.toString().endsWith(".md")).toList()) {
        verifyLocalLinks(document, Files.readString(document));
      }
    }
  }

  private void verifyLocalLinks(Path document, String contents) throws IOException {
    Matcher matcher = MARKDOWN_LINK.matcher(contents);
    while (matcher.find()) {
      String destination = matcher.group(1);
      if (destination.startsWith("http://") || destination.startsWith("https://")) {
        continue;
      }
      String[] parts = destination.split("#", 2);
      Path parent = document.getParent() == null ? Path.of("") : document.getParent();
      Path target = parts[0].isEmpty() ? document : parent.resolve(parts[0]).normalize();
      assertTrue(Files.exists(target), document + " 링크 대상이 없습니다: " + destination);
      if (parts.length == 2 && Files.isRegularFile(target)) {
        Set<String> anchors =
            Files.readAllLines(target).stream()
                .filter(line -> line.startsWith("#"))
                .map(this::headingAnchor)
                .collect(Collectors.toSet());
        assertTrue(anchors.contains(parts[1]), document + " 앵커가 없습니다: " + destination);
      }
    }
  }

  private String headingAnchor(String heading) {
    return heading
        .replaceFirst("^#+\\s*", "")
        .toLowerCase()
        .replaceAll("[^\\p{L}\\p{N} _-]", "")
        .trim()
        .replaceAll(" +", "-");
  }

  private Set<String> implementedTestIds() throws IOException {
    try (var paths = Files.walk(Path.of("src/test/java"))) {
      Set<String> ids = new HashSet<>();
      for (Path path : paths.filter(file -> file.toString().endsWith(".java")).toList()) {
        Matcher testMethods = TEST_METHOD.matcher(Files.readString(path));
        while (testMethods.find()) {
          String annotations = testMethods.group(1);
          if (!annotations.lines().anyMatch(line -> line.trim().equals("@Test"))) {
            continue;
          }
          Matcher displayName = DISPLAY_NAME.matcher(annotations);
          if (displayName.find()) {
            ids.addAll(findIds(displayName.group(1), TEST_ID));
          }
        }
      }
      return Set.copyOf(ids);
    }
  }

  private List<String> requirementHeadings(String prd) {
    return sortedIds(
        prd.lines().filter(line -> line.matches("^#+\\s+(?:FR|NFR)-[0-9]{2}.*")).toList());
  }

  private List<String> requirementRows(String traceability) {
    return sortedIds(
        traceability
            .lines()
            .filter(line -> line.matches("^\\| \\[(?:FR|NFR)-[0-9]{2}].*"))
            .toList());
  }

  private List<String> sortedIds(List<String> lines) {
    List<String> ids = new ArrayList<>();
    for (String line : lines) {
      Matcher matcher = REQUIREMENT_ID.matcher(line);
      if (matcher.find()) {
        ids.add(matcher.group());
      }
    }
    return ids.stream().sorted().toList();
  }

  private Set<String> adrFiles() throws IOException {
    try (var paths = Files.list(Path.of("docs/adr"))) {
      return paths
          .filter(path -> path.getFileName().toString().matches("[0-9]{4}.*\\.md"))
          .map(path -> "./adr/" + path.getFileName())
          .collect(Collectors.toUnmodifiableSet());
    }
  }

  private Set<String> adrLinks(String traceability) {
    Set<String> links = new HashSet<>();
    Matcher matcher = MARKDOWN_LINK.matcher(traceability);
    while (matcher.find()) {
      String destination = matcher.group(1).split("#", 2)[0];
      if (destination.startsWith("./adr/") && !destination.equals("./adr/")) {
        links.add(destination);
      }
    }
    return Set.copyOf(links);
  }

  private Set<String> findIds(String text, Pattern pattern) {
    Set<String> ids = new HashSet<>();
    Matcher matcher = pattern.matcher(text);
    while (matcher.find()) {
      ids.add(matcher.group());
    }
    return Set.copyOf(ids);
  }
}
