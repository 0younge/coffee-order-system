package com.example.coffeeordersystem.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MenuApiTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("AT-MENU-001 메뉴의 현재 이름·가격을 ID 오름차순으로 반환한다")
  void returnsMenusOrderedById() throws Exception {
    Timestamp now = Timestamp.from(Instant.parse("2026-07-16T00:00:00Z"));
    jdbcTemplate.update(
        "INSERT INTO menus (id, name, price, created_at) VALUES (10002, '테스트 큰 메뉴', 7200, ?)", now);
    jdbcTemplate.update(
        "INSERT INTO menus (id, name, price, created_at) VALUES (10001, '테스트 작은 메뉴', 6100, ?)",
        now);
    Long usersBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class);
    Long ordersBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Long.class);
    Long pointsBefore =
        jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(point_balance), 0) FROM users", Long.class);

    mockMvc
        .perform(get("/api/v1/menus").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("MENUS_RETRIEVED"))
        .andExpect(jsonPath("$.message").value("메뉴 목록을 조회했습니다."))
        .andExpect(jsonPath("$.data.length()").value(5))
        .andExpect(jsonPath("$.data[0].menuId").value(1))
        .andExpect(jsonPath("$.data[1].menuId").value(2))
        .andExpect(jsonPath("$.data[2].menuId").value(3))
        .andExpect(jsonPath("$.data[3].menuId").value(10001))
        .andExpect(jsonPath("$.data[3].name").value("테스트 작은 메뉴"))
        .andExpect(jsonPath("$.data[3].price").value(6100))
        .andExpect(jsonPath("$.data[4].menuId").value(10002))
        .andExpect(jsonPath("$.data[4].name").value("테스트 큰 메뉴"))
        .andExpect(jsonPath("$.data[4].price").value(7200));

    assertEquals(
        usersBefore, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class));
    assertEquals(
        ordersBefore, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Long.class));
    assertEquals(
        pointsBefore,
        jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(point_balance), 0) FROM users", Long.class));
  }

  @Test
  @DisplayName("AT-CONTRACT-001 GET은 요청 Content-Type 없이 JSON 봉투를 반환한다")
  void acceptsGetWithoutRequestContentType() throws Exception {
    mockMvc
        .perform(get("/api/v1/menus"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("MENUS_RETRIEVED"))
        .andExpect(jsonPath("$.data").isArray());
  }
}
