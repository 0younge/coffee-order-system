package com.example.coffeeordersystem.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
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

    insertUserMenuAndOrder(suffix, now);

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
                "INSERT INTO idempotency_records "
                    + "(user_id, operation_type, idempotency_key, request_hash, result_code, "
                    + "http_status, response_body, status, created_at, updated_at) "
                    + "VALUES (?, 'CHARGE', '00000000-0000-0000-0000-000000000003', "
                    + "REPEAT('a', 64), 'OK', 200, JSON_OBJECT(), 'PROCESSING', ?, ?)",
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
    assertCheckViolation(
        () ->
            jdbcTemplate.update(
                "INSERT INTO outbox_events "
                    + "(event_id, order_id, event_type, payload, status, retry_count, "
                    + "next_retry_at, created_at) "
                    + "VALUES ('00000000-0000-0000-0000-000000000004', ?, 'ORDER_PAID', "
                    + "JSON_OBJECT(), 'PENDING', 4, ?, ?)",
                suffix,
                now,
                now));
    assertCheckViolation(
        () ->
            jdbcTemplate.update(
                "INSERT INTO outbox_events "
                    + "(event_id, order_id, event_type, payload, status, retry_count, "
                    + "next_retry_at, created_at) "
                    + "VALUES ('00000000-0000-0000-0000-000000000005', ?, 'ORDER_PAID', "
                    + "JSON_OBJECT(), 'PENDING', 1, ?, ?)",
                suffix,
                now,
                now));
    assertCheckViolation(
        () ->
            jdbcTemplate.update(
                "INSERT INTO outbox_events "
                    + "(event_id, order_id, event_type, payload, status, retry_count, "
                    + "failed_at, created_at) "
                    + "VALUES ('00000000-0000-0000-0000-000000000006', ?, 'ORDER_PAID', "
                    + "JSON_OBJECT(), 'FAILED', 1, ?, ?)",
                suffix,
                now,
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

    jdbcTemplate.update(
        "INSERT INTO outbox_events "
            + "(event_id, order_id, event_type, payload, status, retry_count, "
            + "next_retry_at, created_at) "
            + "VALUES ('00000000-0000-0000-0000-000000000007', ?, 'ORDER_PAID', "
            + "JSON_OBJECT(), 'PENDING', 0, ?, ?)",
        suffix,
        now,
        now);
    assertThrows(
        DuplicateKeyException.class,
        () ->
            jdbcTemplate.update(
                "INSERT INTO outbox_events "
                    + "(event_id, order_id, event_type, payload, status, retry_count, "
                    + "next_retry_at, created_at) "
                    + "VALUES ('00000000-0000-0000-0000-000000000008', ?, 'ORDER_PAID', "
                    + "JSON_OBJECT(), 'PENDING', 0, ?, ?)",
                suffix,
                now,
                now));
  }

  @Test
  @Transactional
  @DisplayName("IT-DB-002 상태·유형 코드는 대소문자를 정확히 구분한다")
  void rejectsCaseVariantLifecycleCodes() {
    long suffix = Math.floorMod(System.nanoTime(), 1_000_000_000L) + 1_000_000_000L;
    Timestamp now = Timestamp.from(Instant.parse("2026-07-16T00:00:00Z"));
    insertUserMenuAndOrder(suffix, now);

    assertCheckViolation(
        () ->
            jdbcTemplate.update(
                "INSERT INTO orders "
                    + "(id, user_id, menu_id, menu_name_snapshot, paid_amount, status, "
                    + "paid_at, created_at) VALUES (?, ?, ?, 'test', 4000, 'paid', ?, ?)",
                suffix + 1,
                suffix,
                suffix,
                now,
                now));
    assertCheckViolation(
        () ->
            jdbcTemplate.update(
                "INSERT INTO idempotency_records "
                    + "(user_id, operation_type, idempotency_key, request_hash, status, "
                    + "created_at, updated_at) VALUES (?, 'charge', "
                    + "'10000000-0000-0000-0000-000000000001', REPEAT('a', 64), "
                    + "'PROCESSING', ?, ?)",
                suffix,
                now,
                now));
    assertCheckViolation(
        () ->
            jdbcTemplate.update(
                "INSERT INTO idempotency_records "
                    + "(user_id, operation_type, idempotency_key, request_hash, status, "
                    + "created_at, updated_at) VALUES (?, 'CHARGE', "
                    + "'10000000-0000-0000-0000-000000000002', REPEAT('a', 64), "
                    + "'processing', ?, ?)",
                suffix,
                now,
                now));
    assertCheckViolation(
        () ->
            jdbcTemplate.update(
                "INSERT INTO outbox_events "
                    + "(event_id, order_id, event_type, payload, status, retry_count, "
                    + "next_retry_at, created_at) VALUES ("
                    + "'10000000-0000-0000-0000-000000000003', ?, 'order_paid', "
                    + "JSON_OBJECT(), 'PENDING', 0, ?, ?)",
                suffix,
                now,
                now));
    assertCheckViolation(
        () ->
            jdbcTemplate.update(
                "INSERT INTO outbox_events "
                    + "(event_id, order_id, event_type, payload, status, retry_count, "
                    + "next_retry_at, created_at) VALUES ("
                    + "'10000000-0000-0000-0000-000000000004', ?, 'ORDER_PAID', "
                    + "JSON_OBJECT(), 'pending', 0, ?, ?)",
                suffix,
                now,
                now));
    assertCheckViolation(
        () ->
            jdbcTemplate.update(
                "INSERT INTO outbox_events "
                    + "(event_id, order_id, event_type, payload, status, retry_count, "
                    + "next_retry_at, last_error_type, created_at) VALUES ("
                    + "'10000000-0000-0000-0000-000000000005', ?, 'ORDER_PAID', "
                    + "JSON_OBJECT(), 'PENDING', 1, ?, 'network', ?)",
                suffix,
                now,
                now));
  }

  @Test
  @Transactional
  @DisplayName("IT-DB-002 모든 업무 FK가 고아 행을 거절한다")
  void rejectsMissingForeignKeys() {
    long missingId = Math.floorMod(System.nanoTime(), 1_000_000_000L) + 3_000_000_000L;
    Timestamp now = Timestamp.from(Instant.parse("2026-07-16T00:00:00Z"));

    assertForeignKeyViolation(
        () ->
            jdbcTemplate.update(
                "INSERT INTO orders "
                    + "(user_id, menu_id, menu_name_snapshot, paid_amount, status, "
                    + "paid_at, created_at) VALUES (?, 1, 'test', 4000, 'PAID', ?, ?)",
                missingId,
                now,
                now));
    assertForeignKeyViolation(
        () ->
            jdbcTemplate.update(
                "INSERT INTO idempotency_records "
                    + "(user_id, operation_type, idempotency_key, request_hash, status, "
                    + "created_at, updated_at) VALUES (?, 'CHARGE', "
                    + "'30000000-0000-0000-0000-000000000001', REPEAT('a', 64), "
                    + "'PROCESSING', ?, ?)",
                missingId,
                now,
                now));
    assertForeignKeyViolation(
        () ->
            jdbcTemplate.update(
                "INSERT INTO outbox_events "
                    + "(event_id, order_id, event_type, payload, status, retry_count, "
                    + "next_retry_at, created_at) VALUES ("
                    + "'30000000-0000-0000-0000-000000000002', ?, 'ORDER_PAID', "
                    + "JSON_OBJECT(), 'PENDING', 0, ?, ?)",
                missingId,
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
    List<String> indexes =
        jdbcTemplate.queryForList(
            "SELECT CONCAT(table_name, '|', index_name, '|', seq_in_index, '|', "
                + "column_name, '|', non_unique) FROM information_schema.statistics "
                + "WHERE table_schema = DATABASE() AND index_name IN "
                + "('idx_orders_popular', 'uk_idempotency_request', "
                + "'uk_outbox_order', 'idx_outbox_due') "
                + "ORDER BY table_name, index_name, seq_in_index",
            String.class);
    List<String> foreignKeys =
        jdbcTemplate.queryForList(
            "SELECT CONCAT(k.table_name, '|', k.constraint_name, '|', k.column_name, '|', "
                + "k.referenced_table_name, '|', k.referenced_column_name, '|', "
                + "r.delete_rule, '|', r.update_rule) "
                + "FROM information_schema.key_column_usage k "
                + "JOIN information_schema.referential_constraints r "
                + "ON r.constraint_schema = k.constraint_schema "
                + "AND r.constraint_name = k.constraint_name "
                + "AND r.table_name = k.table_name "
                + "WHERE k.constraint_schema = DATABASE() "
                + "AND k.referenced_table_name IS NOT NULL "
                + "ORDER BY k.table_name, k.constraint_name, k.ordinal_position",
            String.class);
    Set<String> binaryCodeColumns =
        Set.copyOf(
            jdbcTemplate.queryForList(
                "SELECT CONCAT(table_name, '|', column_name) "
                    + "FROM information_schema.columns WHERE table_schema = DATABASE() "
                    + "AND collation_name = 'ascii_bin'",
                String.class));

    assertEquals(expectedColumns, actualColumns);
    assertEquals(
        List.of(
            "idempotency_records|uk_idempotency_request|1|user_id|0",
            "idempotency_records|uk_idempotency_request|2|operation_type|0",
            "idempotency_records|uk_idempotency_request|3|idempotency_key|0",
            "orders|idx_orders_popular|1|status|1",
            "orders|idx_orders_popular|2|paid_at|1",
            "orders|idx_orders_popular|3|menu_id|1",
            "outbox_events|idx_outbox_due|1|status|1",
            "outbox_events|idx_outbox_due|2|next_retry_at|1",
            "outbox_events|uk_outbox_order|1|order_id|0"),
        indexes);
    assertEquals(
        List.of(
            "idempotency_records|fk_idempotency_user|user_id|users|id|RESTRICT|RESTRICT",
            "orders|fk_orders_menu|menu_id|menus|id|RESTRICT|RESTRICT",
            "orders|fk_orders_user|user_id|users|id|RESTRICT|RESTRICT",
            "outbox_events|fk_outbox_order|order_id|orders|id|RESTRICT|RESTRICT"),
        foreignKeys);
    assertEquals(
        Set.of(
            "orders|status",
            "idempotency_records|operation_type",
            "idempotency_records|idempotency_key",
            "idempotency_records|request_hash",
            "idempotency_records|result_code",
            "idempotency_records|status",
            "outbox_events|event_id",
            "outbox_events|event_type",
            "outbox_events|status",
            "outbox_events|claim_token",
            "outbox_events|last_error_type"),
        binaryCodeColumns);
  }

  @Test
  @Transactional
  @DisplayName("IT-TIME-001 비 UTC JVM에서도 DATETIME을 UTC 기준값으로 저장한다")
  void storesDatetimeAsUtcWithNonUtcJvmDefault() {
    synchronized (TimeZone.class) {
      TimeZone previous = TimeZone.getDefault();
      try {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
        long userId = Math.floorMod(System.nanoTime(), 1_000_000_000L) + 4_000_000_000L;
        Timestamp timestamp = Timestamp.from(Instant.parse("2026-07-16T12:34:56.123456Z"));

        jdbcTemplate.update(
            "INSERT INTO users (id, point_balance, created_at, updated_at) VALUES (?, 0, ?, ?)",
            userId,
            timestamp,
            timestamp);

        assertEquals(
            "2026-07-16 12:34:56.123456",
            jdbcTemplate.queryForObject(
                "SELECT DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s.%f') "
                    + "FROM users WHERE id = ?",
                String.class, userId));
        assertEquals(
            "+00:00", jdbcTemplate.queryForObject("SELECT @@session.time_zone", String.class));
      } finally {
        TimeZone.setDefault(previous);
      }
    }
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

  private void assertForeignKeyViolation(Runnable statement) {
    DataIntegrityViolationException exception =
        assertThrows(DataIntegrityViolationException.class, statement::run);
    SQLException sqlException = assertInstanceOf(SQLException.class, exception.getRootCause());
    assertEquals(1452, sqlException.getErrorCode());
  }

  private void insertUserMenuAndOrder(long id, Timestamp now) {
    jdbcTemplate.update(
        "INSERT INTO users (id, point_balance, created_at, updated_at) VALUES (?, 10000, ?, ?)",
        id,
        now,
        now);
    jdbcTemplate.update(
        "INSERT INTO menus (id, name, price, created_at) VALUES (?, 'test', 4000, ?)", id, now);
    jdbcTemplate.update(
        "INSERT INTO orders "
            + "(id, user_id, menu_id, menu_name_snapshot, paid_amount, status, paid_at, created_at) "
            + "VALUES (?, ?, ?, 'test', 4000, 'PAID', ?, ?)",
        id,
        id,
        id,
        now,
        now);
  }
}
