package com.example.coffeeordersystem.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PopularMenuApiTest {

  private static final AtomicLong ID_SEQUENCE = new AtomicLong(8_800_000_000L);
  private static final Instant NOW = Instant.parse("2026-07-16T12:00:00Z");

  @Autowired private MockMvc mockMvc;

  @Autowired private JdbcTemplate jdbcTemplate;

  @MockitoBean private Clock clock;

  private long userId;
  private long firstMenuId;

  @BeforeEach
  void setUp() {
    userId = ID_SEQUENCE.addAndGet(10);
    firstMenuId = userId + 1;
    when(clock.instant()).thenReturn(NOW);
    Timestamp now = Timestamp.from(NOW);
    jdbcTemplate.update(
        "INSERT INTO users (id, point_balance, created_at, updated_at) VALUES (?, 0, ?, ?)",
        userId,
        now,
        now);
    for (int index = 0; index < 5; index++) {
      insertMenu(firstMenuId + index, "현재 메뉴 " + (index + 1), 1_000L + index);
    }
  }

  @AfterEach
  void tearDown() {
    jdbcTemplate.update("DELETE FROM orders WHERE user_id = ?", userId);
    jdbcTemplate.update("DELETE FROM menus WHERE id BETWEEN ? AND ?", firstMenuId, firstMenuId + 4);
    jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
  }

  @Test
  @DisplayName("IT-POPULAR-001 7일 하한은 포함하고 상한과 범위 밖 주문은 제외한다")
  void aggregatesOnlyHalfOpenSevenDayWindow() throws Exception {
    Instant from = NOW.minus(7, ChronoUnit.DAYS);
    insertOrder(firstMenuId, from);
    insertOrder(firstMenuId, from.minus(1, ChronoUnit.MICROS));
    insertOrder(firstMenuId + 1, NOW.minus(1, ChronoUnit.MICROS));
    insertOrder(firstMenuId + 1, NOW);

    mockMvc
        .perform(get("/api/v1/menus/popular"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].menuId").value(firstMenuId))
        .andExpect(jsonPath("$.data[0].orderCount").value(1))
        .andExpect(jsonPath("$.data[1].menuId").value(firstMenuId + 1))
        .andExpect(jsonPath("$.data[1].orderCount").value(1));

    verify(clock, times(1)).instant();
  }

  @Test
  @DisplayName("IT-POPULAR-002 AT-POPULAR-002 주문 수와 동률 순서로 상위 3개를 반환한다")
  void returnsTopThreeWithDeterministicTies() throws Exception {
    insertOrders(firstMenuId + 3, 2);
    insertOrders(firstMenuId + 2, 3);
    insertOrders(firstMenuId + 1, 4);
    insertOrders(firstMenuId, 4);

    mockMvc
        .perform(get("/api/v1/menus/popular"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("POPULAR_MENUS_RETRIEVED"))
        .andExpect(jsonPath("$.message").value("인기 메뉴를 조회했습니다."))
        .andExpect(jsonPath("$.data.length()").value(3))
        .andExpect(jsonPath("$.data[0].rank").value(1))
        .andExpect(jsonPath("$.data[0].menuId").value(firstMenuId))
        .andExpect(jsonPath("$.data[0].orderCount").value(4))
        .andExpect(jsonPath("$.data[1].rank").value(2))
        .andExpect(jsonPath("$.data[1].menuId").value(firstMenuId + 1))
        .andExpect(jsonPath("$.data[1].orderCount").value(4))
        .andExpect(jsonPath("$.data[2].rank").value(3))
        .andExpect(jsonPath("$.data[2].menuId").value(firstMenuId + 2))
        .andExpect(jsonPath("$.data[2].orderCount").value(3));
  }

  @Test
  @DisplayName("IT-POPULAR-003 현재 메뉴 정보와 주문 원본의 횟수를 결합한다")
  void returnsCurrentMenuMetadataWithHistoricalCount() throws Exception {
    insertOrder(firstMenuId, NOW.minus(1, ChronoUnit.DAYS));
    insertOrder(firstMenuId, NOW.minus(2, ChronoUnit.DAYS));
    jdbcTemplate.update(
        "UPDATE menus SET name = '변경된 현재 메뉴', price = 9000 WHERE id = ?", firstMenuId);

    mockMvc
        .perform(get("/api/v1/menus/popular"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].rank").value(1))
        .andExpect(jsonPath("$.data[0].menuId").value(firstMenuId))
        .andExpect(jsonPath("$.data[0].name").value("변경된 현재 메뉴"))
        .andExpect(jsonPath("$.data[0].price").value(9_000L))
        .andExpect(jsonPath("$.data[0].orderCount").value(2));
  }

  @Test
  @DisplayName("AT-POPULAR-001 대상 주문이 없으면 빈 배열을 반환하고 상태를 변경하지 않는다")
  void returnsEmptyArrayWithoutChangingState() throws Exception {
    long usersBefore = count("SELECT COUNT(*) FROM users");
    long menusBefore = count("SELECT COUNT(*) FROM menus");
    long ordersBefore = count("SELECT COUNT(*) FROM orders");

    mockMvc
        .perform(get("/api/v1/menus/popular"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data").isEmpty());

    assertEquals(usersBefore, count("SELECT COUNT(*) FROM users"));
    assertEquals(menusBefore, count("SELECT COUNT(*) FROM menus"));
    assertEquals(ordersBefore, count("SELECT COUNT(*) FROM orders"));
  }

  private void insertOrders(long menuId, int count) {
    for (int index = 0; index < count; index++) {
      insertOrder(menuId, NOW.minus(index + 1L, ChronoUnit.HOURS));
    }
  }

  private void insertOrder(long menuId, Instant paidAt) {
    Timestamp timestamp = Timestamp.from(paidAt);
    jdbcTemplate.update(
        "INSERT INTO orders "
            + "(user_id, menu_id, menu_name_snapshot, paid_amount, status, paid_at, created_at) "
            + "VALUES (?, ?, '과거 메뉴', 500, 'PAID', ?, ?)",
        userId,
        menuId,
        timestamp,
        timestamp);
  }

  private void insertMenu(long menuId, String name, long price) {
    jdbcTemplate.update(
        "INSERT INTO menus (id, name, price, created_at) VALUES (?, ?, ?, ?)",
        menuId,
        name,
        price,
        Timestamp.from(NOW));
  }

  private long count(String sql) {
    Long value = jdbcTemplate.queryForObject(sql, Long.class);
    return value == null ? 0L : value;
  }
}
