package com.example.coffeeordersystem.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class TimeConfigurationTest {

  @Autowired private Clock clock;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("IT-TIME-001 애플리케이션과 DB 세션은 UTC를 사용한다")
  void usesUtcForApplicationAndDatabase() {
    assertEquals(ZoneOffset.UTC, clock.getZone());
    assertEquals("+00:00", jdbcTemplate.queryForObject("SELECT @@session.time_zone", String.class));
    assertEquals(
        5, jdbcTemplate.queryForObject("SELECT @@session.innodb_lock_wait_timeout", Integer.class));
  }
}
