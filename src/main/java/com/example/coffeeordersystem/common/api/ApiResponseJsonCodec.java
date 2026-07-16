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
    return canonicalize(objectMapper.valueToTree(response));
  }

  public JsonNode read(String responseBody) {
    return objectMapper.readTree(responseBody);
  }

  public String compact(String responseBody) {
    return canonicalize(read(responseBody));
  }

  private String canonicalize(JsonNode node) {
    StringBuilder json = new StringBuilder();
    appendCanonical(node, json);
    return json.toString();
  }

  private void appendCanonical(JsonNode node, StringBuilder json) {
    if (node.isObject()) {
      json.append('{');
      var properties =
          node.properties().stream()
              .sorted((left, right) -> left.getKey().compareTo(right.getKey()))
              .toList();
      for (int index = 0; index < properties.size(); index++) {
        if (index > 0) {
          json.append(',');
        }
        var property = properties.get(index);
        json.append(objectMapper.writeValueAsString(property.getKey())).append(':');
        appendCanonical(property.getValue(), json);
      }
      json.append('}');
      return;
    }
    if (node.isArray()) {
      json.append('[');
      for (int index = 0; index < node.size(); index++) {
        if (index > 0) {
          json.append(',');
        }
        appendCanonical(node.get(index), json);
      }
      json.append(']');
      return;
    }
    json.append(objectMapper.writeValueAsString(node));
  }
}
