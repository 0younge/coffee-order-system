package com.example.coffeeordersystem.quality;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BuildConfigurationTest {

  @Test
  @DisplayName("QT-CONFIG-002 Java·Spring·Gradle·MySQL 기술 기준선이 일치한다")
  void matchesApprovedPlatformVersions() throws IOException {
    String build = Files.readString(Path.of("build.gradle"));
    String wrapper = Files.readString(Path.of("gradle/wrapper/gradle-wrapper.properties"));
    String compose = Files.readString(Path.of("compose.yaml"));

    assertTrue(build.contains("org.springframework.boot' version '4.1.0'"));
    assertTrue(build.contains("JavaLanguageVersion.of(17)"));
    assertTrue(wrapper.contains("gradle-9.5.1-bin.zip"));
    assertTrue(compose.contains("image: mysql:8.4"));
  }

  @Test
  @DisplayName("QT-DEPS-001 추가 애플리케이션 의존성은 승인 목록에 한정된다")
  void usesOnlyApprovedApplicationDependencies() throws IOException {
    String build = Files.readString(Path.of("build.gradle"));

    assertTrue(build.contains("spring-boot-starter-validation"));
    assertTrue(build.contains("spring-boot-starter-actuator"));
    assertTrue(build.contains("spring-boot-flyway"));
    assertTrue(build.contains("org.flywaydb:flyway-core"));
    assertTrue(build.contains("org.flywaydb:flyway-mysql"));
    assertFalse(build.contains("spring-boot-starter-security"));
    assertFalse(build.contains("lombok"));
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
    String migration =
        Files.readString(
            Path.of(
                "src/main/resources/db/migration/V1__create_schema_and_seed_reference_data.sql"));
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
      assertTrue(migration.contains(schemaName));
    }
  }
}
