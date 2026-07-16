package com.example.coffeeordersystem.common.api;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
@Component
public class ApiResponseJsonCodec {

  private final ObjectMapper objectMapper;

  public String write(Object response) {
    return objectMapper.valueToTree(response).toString();
  }

  public JsonNode read(String responseBody) {
    return objectMapper.readTree(responseBody);
  }

  public String compact(String responseBody) {
    return read(responseBody).toString();
  }
}
