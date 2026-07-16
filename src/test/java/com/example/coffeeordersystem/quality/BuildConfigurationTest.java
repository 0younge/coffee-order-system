package com.example.coffeeordersystem.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BuildConfigurationTest {

  @Test
  @DisplayName("QT-CONFIG-002 Java·Spring·Gradle·MySQL 기술 기준선이 일치한다")
  void matchesApprovedPlatformVersions() throws IOException {
    String build = Files.readString(Path.of("build.gradle"));
    String wrapper = Files.readString(Path.of("gradle/wrapper/gradle-wrapper.properties"));
    String compose = Files.readString(Path.of("compose.yaml"));
    String application = Files.readString(Path.of("src/main/resources/application.yaml"));
    String testApplication = Files.readString(Path.of("src/test/resources/application-test.yaml"));
    String environmentExample = Files.readString(Path.of(".env.example"));

    assertTrue(build.contains("org.springframework.boot' version '4.1.0'"));
    assertTrue(build.contains("JavaLanguageVersion.of(17)"));
    assertTrue(wrapper.contains("gradle-9.5.1-bin.zip"));
    assertTrue(compose.contains("image: mysql:8.4"));
    assertTrue(application.contains("connectionTimeZone=UTC"));
    assertTrue(testApplication.contains("connectionTimeZone=UTC"));
    assertTrue(
        environmentExample.contains(
            "DB_URL=jdbc:mysql://localhost:3307/coffee_order_system?connectionTimeZone=UTC"));
    assertTrue(
        environmentExample.contains(
            "TEST_DB_URL=jdbc:mysql://localhost:3307/coffee_order_system_test"
                + "?connectionTimeZone=UTC"));
  }

  @Test
  @DisplayName("QT-DEPS-001 추가 애플리케이션 의존성은 승인 목록에 한정된다")
  void usesOnlyApprovedApplicationDependencies() throws IOException {
    String build = Files.readString(Path.of("build.gradle"));

    assertEquals(
        Set.of(
            "implementation|org.springframework.boot:spring-boot-starter-actuator",
            "implementation|org.springframework.boot:spring-boot-starter-data-jpa",
            "implementation|org.springframework.boot:spring-boot-starter-validation",
            "implementation|org.springframework.boot:spring-boot-starter-webmvc",
            "implementation|org.springframework.boot:spring-boot-flyway",
            "implementation|org.flywaydb:flyway-core",
            "implementation|org.flywaydb:flyway-mysql",
            "runtimeOnly|com.mysql:mysql-connector-j",
            "testImplementation|org.springframework.boot:spring-boot-starter-data-jpa-test",
            "testImplementation|org.springframework.boot:spring-boot-starter-webmvc-test",
            "testRuntimeOnly|org.junit.platform:junit-platform-launcher"),
        declaredDependencies(build));
  }

  @Test
  @DisplayName("QT-FORMAT-001 Spotless 검사는 check에 포함되고 자동 수정과 분리된다")
  void configuresSpotlessAsCheckGate() throws IOException {
    String build = Files.readString(Path.of("build.gradle"));

    assertTrue(build.contains("id 'com.diffplug.spotless' version '8.8.0'"));
    assertTrue(build.contains("googleJavaFormat('1.24.0')"));
    assertTrue(build.contains("dependsOn tasks.named('spotlessCheck')"));
    assertFalse(build.contains("spotlessApply.finalizedBy"));
  }

  @Test
  @DisplayName("QT-CONFIG-003 Compose가 멱등 테스트 DB 초기화 스크립트를 연결한다")
  void configuresIsolatedTestDatabaseInitialization() throws IOException {
    String compose = Files.readString(Path.of("compose.yaml"));
    String script = Files.readString(Path.of("docker/mysql/init/01-create-test-database.sh"));

    assertTrue(compose.contains("TEST_DB_NAME"));
    assertTrue(compose.contains("01-create-test-database.sh"));
    assertTrue(script.contains("CREATE DATABASE IF NOT EXISTS"));
    assertTrue(script.contains("GRANT ALL PRIVILEGES"));
    assertFalse(script.contains("DROP DATABASE"));
  }

  @Test
  @DisplayName("QT-SCHEMA-001 Flyway migration이 ERD의 테이블과 필드를 포함한다")
  void migrationContainsDocumentedSchema() throws IOException {
    String erd = Files.readString(Path.of("docs/erd.md"));
    String initialMigration =
        Files.readString(
            Path.of(
                "src/main/resources/db/migration/V1__create_schema_and_seed_reference_data.sql"));
    String lifecycleMigration =
        Files.readString(
            Path.of("src/main/resources/db/migration/V2__make_lifecycle_codes_case_sensitive.sql"));
    String outboxClaimMigration =
        Files.readString(Path.of("src/main/resources/db/migration/V3__optimize_outbox_claim.sql"));
    String[] schemaNames = {
      "users",
      "menus",
      "orders",
      "idempotency_records",
      "outbox_events",
      "point_balance",
      "menu_name_snapshot",
      "paid_amount",
      "idempotency_key",
      "request_hash",
      "response_body",
      "next_retry_at",
      "claim_token",
      "last_error_type"
    };

    for (String schemaName : schemaNames) {
      assertTrue(erd.contains(schemaName));
      assertTrue(initialMigration.contains(schemaName));
    }
    assertTrue(lifecycleMigration.contains("CHARACTER SET ascii COLLATE ascii_bin"));
    assertTrue(outboxClaimMigration.contains("idx_outbox_claim_order"));
    assertTrue(outboxClaimMigration.contains("next_retry_at, created_at, event_id"));
  }

  private Set<String> declaredDependencies(String build) {
    boolean[] inDependencies = {false};
    return Set.copyOf(
        build
            .lines()
            .map(String::trim)
            .takeWhile(line -> !inDependencies[0] || !line.equals("}"))
            .filter(
                line -> {
                  if (line.equals("dependencies {")) {
                    inDependencies[0] = true;
                    return false;
                  }
                  return inDependencies[0] && !line.isBlank();
                })
            .map(
                line -> {
                  int separator = line.indexOf(' ');
                  int firstQuote = line.indexOf('\'');
                  int lastQuote = line.lastIndexOf('\'');
                  return line.substring(0, separator)
                      + "|"
                      + line.substring(firstQuote + 1, lastQuote);
                })
            .toList());
  }
}
