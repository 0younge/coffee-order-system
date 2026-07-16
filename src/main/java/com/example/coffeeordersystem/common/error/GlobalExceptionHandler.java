package com.example.coffeeordersystem.common.error;

import com.example.coffeeordersystem.common.api.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException exception) {
    return errorResponse(exception.errorCode());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
    log.error("예상하지 못한 API 오류 errorType={}", exception.getClass().getSimpleName());
    return errorResponse(ErrorCode.INTERNAL_SERVER_ERROR);
  }

  private ResponseEntity<ApiResponse<Void>> errorResponse(ErrorCode errorCode) {
    return ResponseEntity.status(errorCode.httpStatus())
        .body(ApiResponse.failure(errorCode.name(), errorCode.message()));
  }
}
