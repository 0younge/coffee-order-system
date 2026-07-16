package com.example.coffeeordersystem;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CoffeeOrderSystemApplicationTests {

  @Autowired private Environment environment;

  @Test
  void contextLoads() {}

  @Test
  void importsDotenvAsOptionalProperties() {
    assertEquals(
        "optional:file:.env[.properties]", environment.getProperty("spring.config.import"));
    assertEquals(
        environment.getProperty("DB_PASSWORD"),
        environment.getProperty("spring.datasource.password"));
    assertEquals("false", environment.getProperty("outbox.worker.enabled"));
  }
}
