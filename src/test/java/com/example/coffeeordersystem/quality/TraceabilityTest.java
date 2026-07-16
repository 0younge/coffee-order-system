package com.example.coffeeordersystem.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TraceabilityTest {

  private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[[^]]+]\\(([^)]+)\\)");
  private static final Pattern REQUIREMENT_ID = Pattern.compile("(?:FR|NFR)-[0-9]{2}");
  private static final Pattern TEST_ID = Pattern.compile("[A-Z]{2,4}-[A-Z]+-[0-9]{3}");

  @Test
  @DisplayName("QT-TRACE-001 README·요구사항·ADR·테스트 ID의 참조가 끊기지 않는다")
  void keepsDocumentationAndTestsTraceable() throws IOException {
    String readme = Files.readString(Path.of("README.md"));
    String prd = Files.readString(Path.of("docs/prd.md"));
    String testStrategy = Files.readString(Path.of("docs/test-strategy.md"));
    String traceability = Files.readString(Path.of("docs/requirements-traceability.md"));

    verifyReadmeLinks(readme);
    assertTrue(findIds(traceability, REQUIREMENT_ID).containsAll(findIds(prd, REQUIREMENT_ID)));

    try (var paths = Files.list(Path.of("docs/adr"))) {
      for (Path adr :
          paths.filter(path -> path.getFileName().toString().matches("[0-9]{4}.*\\.md")).toList()) {
        assertTrue(
            traceability.contains("./adr/" + adr.getFileName()), adr + "가 요구사항 추적표에 연결되어야 합니다.");
      }
    }

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
  }

  private void verifyReadmeLinks(String readme) throws IOException {
    Matcher matcher = MARKDOWN_LINK.matcher(readme);
    while (matcher.find()) {
      String destination = matcher.group(1);
      if (destination.startsWith("http://") || destination.startsWith("https://")) {
        continue;
      }
      String[] parts = destination.split("#", 2);
      Path target = Path.of(parts[0]).normalize();
      assertTrue(Files.exists(target), "README 링크 대상이 없습니다: " + destination);
      if (parts.length == 2 && Files.isRegularFile(target)) {
        Set<String> anchors =
            Files.readAllLines(target).stream()
                .filter(line -> line.startsWith("#"))
                .map(this::headingAnchor)
                .collect(Collectors.toSet());
        assertTrue(anchors.contains(parts[1]), "README 앵커가 없습니다: " + destination);
      }
    }
  }

  private String headingAnchor(String heading) {
    return heading
        .replaceFirst("^#+\\s*", "")
        .toLowerCase()
        .replaceAll("[^\\p{L}\\p{N} _-]", "")
        .trim()
        .replaceAll("[ _]+", "-");
  }

  private Set<String> implementedTestIds() throws IOException {
    try (var paths = Files.walk(Path.of("src/test/java"))) {
      Set<String> ids = new HashSet<>();
      for (Path path : paths.filter(file -> file.toString().endsWith(".java")).toList()) {
        ids.addAll(findIds(Files.readString(path), TEST_ID));
      }
      return Set.copyOf(ids);
    }
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
