package com.example.coffeeordersystem.common.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.example.coffeeordersystem.common.api.ApiResponse;
import com.example.coffeeordersystem.common.observability.DatabaseContentionMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler =
      new GlobalExceptionHandler(new DatabaseContentionMetrics(new SimpleMeterRegistry()));

  @Test
  @DisplayName("UT-API-001 공개 오류 코드와 HTTP 상태를 일관되게 매핑한다")
  void mapsPublicErrorCodes() {
    for (ErrorCode errorCode : ErrorCode.values()) {
      ResponseEntity<ApiResponse<Void>> response =
          handler.handleApiException(new ApiException(errorCode));
      ApiResponse<Void> body = response.getBody();

      assertEquals(errorCode.httpStatus(), response.getStatusCode());
      assertEquals(false, body.success());
      assertEquals(errorCode.name(), body.code());
      assertEquals(errorCode.message(), body.message());
      assertNull(body.data());
    }
  }

  @Test
  @DisplayName("UT-API-001 예상하지 못한 오류의 내부 정보를 응답에 노출하지 않는다")
  void hidesUnexpectedExceptionDetails() {
    ResponseEntity<ApiResponse<Void>> response =
        handler.handleUnexpectedException(
            new IllegalStateException("SQL syntax error password=top-secret"));
    ApiResponse<Void> body = response.getBody();

    assertEquals(ErrorCode.INTERNAL_SERVER_ERROR.httpStatus(), response.getStatusCode());
    assertEquals("INTERNAL_SERVER_ERROR", body.code());
    assertFalse(body.message().contains("SQL"));
    assertFalse(body.message().contains("password"));
    assertNull(body.data());
  }
}
