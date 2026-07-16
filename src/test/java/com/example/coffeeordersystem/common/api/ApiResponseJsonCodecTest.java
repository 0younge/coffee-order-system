package com.example.coffeeordersystem.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ApiResponseJsonCodecTest {

  private final ApiResponseJsonCodec codec = new ApiResponseJsonCodec(new ObjectMapper());

  @Test
  @DisplayName("UT-IDEM-003 멱등 응답의 객체 키를 재귀적으로 결정 정렬한다")
  void canonicalizesObjectKeysRecursively() {
    String expected =
        "{\"code\":\"OK\",\"data\":{\"a\":[{\"a\":3,\"z\":4},{\"a\":5,\"z\":6}],\"z\":2},"
            + "\"message\":\"완료\",\"success\":true}";

    assertEquals(
        expected,
        codec.write(
            ApiResponse.success(
                "OK",
                "완료",
                Map.of("z", 2, "a", List.of(Map.of("z", 4, "a", 3), Map.of("z", 6, "a", 5))))));
    assertEquals(
        expected,
        codec.compact(
            "{\"success\":true,\"message\":\"완료\",\"data\":{\"z\":2,\"a\":[{\"z\":4,\"a\":3},{\"z\":6,\"a\":5}]},\"code\":\"OK\"}"));
  }
}
