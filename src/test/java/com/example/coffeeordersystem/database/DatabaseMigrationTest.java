package com.example.coffeeordersystem.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
class DatabaseMigrationTest {

  private static final Set<String> DOMAIN_TABLES =
      Set.of("users", "menus", "orders", "idempotency_records", "outbox_events");

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private Environment environment;

  @Test
  @DisplayName("IT-DB-001 Flyway가 빈 MySQL에 스키마와 기준 데이터를 적용한다")
  void appliesSchemaAndReferenceData() {
    Set<String> tables =
        Set.copyOf(
            jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables "
                    + "WHERE table_schema = DATABASE() "
                    + "AND table_name IN ('users', 'menus', 'orders', "
                    + "'idempotency_records', 'outbox_events')",
                String.class));

    assertEquals(DOMAIN_TABLES, tables);
    assertEquals(1L, count("SELECT COUNT(*) FROM users WHERE id = 1 AND point_balance = 0"));
    assertEquals(3L, count("SELECT COUNT(*) FROM menus WHERE id BETWEEN 1 AND 3"));
    assertEquals(
        List.of("아메리카노", "카페라떼", "카푸치노"),
        jdbcTemplate.queryForList("SELECT name FROM menus ORDER BY id", String.class));
    assertEquals(
        1L,
        count(
            "SELECT COUNT(*) FROM flyway_schema_history " + "WHERE version = '1' AND success = 1"));
  }

  @Test
  @Transactional
  @DisplayName("IT-DB-002 MySQL CHECK와 UNIQUE가 수명주기 불변식을 거절한다")
  void rejectsInvalidLifecycleStates() {
    long suffix = Math.floorMod(System.nanoTime(), 1_000_000_000L) + 10_000L;
    Timestamp now = Timestamp.from(Instant.parse("2026-07-16T00:00:00Z"));

    assertCheckViolation(
        () ->
            jdbcTemplate.update(
                "INSERT INTO users "
                    + "(id, point_balance, created_at, updated_at) VALUES (?, -1, ?, ?)",
                suffix,
                now,
                now));
    assertCheckViolation(
        () ->
            jdbcTemplate.update(
                "INSERT INTO menus (id, name, price, created_at) VALUES (?, 'invalid', 0, ?)",
                suffix,
                now));

    jdbcTemplate.update(
        "INSERT INTO users (id, point_balance, created_at, updated_at) VALUES (?, 10000, ?, ?)",
        suffix,
        now,
        now);
    jdbcTemplate.update(
        "INSERT INTO menus (id, name, price, created_at) VALUES (?, 'test', 4000, ?)", suffix, now);
    jdbcTemplate.update(
        "INSERT INTO orders "
            + "(id, user_id, menu_id, menu_name_snapshot, paid_amount, status, paid_at, created_at) "
            + "VALUES (?, ?, ?, 'test', 4000, 'PAID', ?, ?)",
        suffix,
        suffix,
        suffix,
        now,
        now);

    assertCheckViolation(
        () ->
            jdbcTemplate.update(
                "INSERT INTO idempotency_records "
                    + "(user_id, operation_type, idempotency_key, request_hash, status, "
                    + "created_at, updated_at) "
                    + "VALUES (?, 'CHARGE', '00000000-0000-0000-0000-000000000001', "
                    + "REPEAT('a', 64), 'COMPLETED', ?, ?)",
                suffix,
                now,
                now));
    assertCheckViolation(
        () ->
            jdbcTemplate.update(
                "INSERT INTO outbox_events "
                    + "(event_id, order_id, event_type, payload, status, retry_count, created_at) "
                    + "VALUES ('00000000-0000-0000-0000-000000000001', ?, 'ORDER_PAID', "
                    + "JSON_OBJECT(), 'PUBLISHED', 0, ?)",
                suffix,
                now));

    jdbcTemplate.update(
        "INSERT INTO idempotency_records "
            + "(user_id, operation_type, idempotency_key, request_hash, status, "
            + "created_at, updated_at) "
            + "VALUES (?, 'CHARGE', '00000000-0000-0000-0000-000000000002', "
            + "REPEAT('a', 64), 'PROCESSING', ?, ?)",
        suffix,
        now,
        now);
    assertThrows(
        DuplicateKeyException.class,
        () ->
            jdbcTemplate.update(
                "INSERT INTO idempotency_records "
                    + "(user_id, operation_type, idempotency_key, request_hash, status, "
                    + "created_at, updated_at) "
                    + "VALUES (?, 'CHARGE', '00000000-0000-0000-0000-000000000002', "
                    + "REPEAT('b', 64), 'PROCESSING', ?, ?)",
                suffix,
                now,
                now));
  }

  @Test
  @DisplayName("IT-DB-003 실제 컬럼과 핵심 인덱스가 ERD와 일치한다")
  void matchesDocumentedColumnsAndIndexes() {
    Set<String> expectedColumns =
        Set.of(
            "users|id|bigint|NO",
            "users|point_balance|bigint|NO",
            "users|created_at|datetime|NO",
            "users|updated_at|datetime|NO",
            "menus|id|bigint|NO",
            "menus|name|varchar|NO",
            "menus|price|bigint|NO",
            "menus|created_at|datetime|NO",
            "orders|id|bigint|NO",
            "orders|user_id|bigint|NO",
            "orders|menu_id|bigint|NO",
            "orders|menu_name_snapshot|varchar|NO",
            "orders|paid_amount|bigint|NO",
            "orders|status|varchar|NO",
            "orders|paid_at|datetime|NO",
            "orders|created_at|datetime|NO",
            "idempotency_records|id|bigint|NO",
            "idempotency_records|user_id|bigint|NO",
            "idempotency_records|operation_type|varchar|NO",
            "idempotency_records|idempotency_key|char|NO",
            "idempotency_records|request_hash|char|NO",
            "idempotency_records|result_code|varchar|YES",
            "idempotency_records|http_status|int|YES",
            "idempotency_records|response_body|json|YES",
            "idempotency_records|status|varchar|NO",
            "idempotency_records|completed_at|datetime|YES",
            "idempotency_records|created_at|datetime|NO",
            "idempotency_records|updated_at|datetime|NO",
            "outbox_events|event_id|char|NO",
            "outbox_events|order_id|bigint|NO",
            "outbox_events|event_type|varchar|NO",
            "outbox_events|payload|json|NO",
            "outbox_events|status|varchar|NO",
            "outbox_events|retry_count|int|NO",
            "outbox_events|next_retry_at|datetime|YES",
            "outbox_events|locked_at|datetime|YES",
            "outbox_events|claim_token|char|YES",
            "outbox_events|published_at|datetime|YES",
            "outbox_events|failed_at|datetime|YES",
            "outbox_events|last_http_status|int|YES",
            "outbox_events|last_error_type|varchar|YES",
            "outbox_events|created_at|datetime|NO");
    Set<String> actualColumns =
        Set.copyOf(
            jdbcTemplate.queryForList(
                "SELECT CONCAT(table_name, '|', column_name, '|', data_type, '|', is_nullable) "
                    + "FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() "
                    + "AND table_name IN ('users', 'menus', 'orders', "
                    + "'idempotency_records', 'outbox_events')",
                String.class));
    Set<String> indexes =
        jdbcTemplate
            .queryForList(
                "SELECT DISTINCT index_name FROM information_schema.statistics "
                    + "WHERE table_schema = DATABASE() "
                    + "AND table_name IN ('users', 'menus', 'orders', "
                    + "'idempotency_records', 'outbox_events')",
                String.class)
            .stream()
            .collect(Collectors.toSet());

    assertEquals(expectedColumns, actualColumns);
    assertTrue(indexes.contains("idx_orders_popular"));
    assertTrue(indexes.contains("uk_idempotency_request"));
    assertTrue(indexes.contains("uk_outbox_order"));
    assertTrue(indexes.contains("idx_outbox_due"));
  }

  @Test
  @Transactional
  @DisplayName("IT-DB-004 통합 테스트는 개발 DB와 분리된 테스트 DB만 변경한다")
  void isolatesTestDatabaseFromDevelopmentDatabase() {
    String testDatabase = environment.getProperty("TEST_DB_NAME", "coffee_order_system_test");
    String developmentDatabase = environment.getProperty("DB_NAME", "coffee_order_system");
    String currentDatabase = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
    long userId = Math.floorMod(System.nanoTime(), 1_000_000_000L) + 2_000_000_000L;
    Timestamp now = Timestamp.from(Instant.parse("2026-07-16T00:00:00Z"));

    assertEquals(testDatabase, currentDatabase);
    assertFalse(testDatabase.equals(developmentDatabase));

    jdbcTemplate.update(
        "INSERT INTO users (id, point_balance, created_at, updated_at) VALUES (?, 0, ?, ?)",
        userId,
        now,
        now);
    assertEquals(1L, count("SELECT COUNT(*) FROM users WHERE id = " + userId));

    Integer developmentUsersTable =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = ? AND table_name = 'users'",
            Integer.class,
            developmentDatabase);
    if (developmentUsersTable != null && developmentUsersTable == 1) {
      assertEquals(
          0L,
          count("SELECT COUNT(*) FROM `" + developmentDatabase + "`.users WHERE id = " + userId));
    }
  }

  private long count(String sql) {
    Long value = jdbcTemplate.queryForObject(sql, Long.class);
    return value == null ? 0L : value;
  }

  private void assertCheckViolation(Runnable statement) {
    DataAccessException exception = assertThrows(DataAccessException.class, statement::run);
    SQLException sqlException = assertInstanceOf(SQLException.class, exception.getRootCause());
    assertEquals(3819, sqlException.getErrorCode());
  }
}
