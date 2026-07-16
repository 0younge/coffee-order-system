package com.example.coffeeordersystem.common.error;

import com.example.coffeeordersystem.common.api.ApiResponse;
import com.example.coffeeordersystem.common.observability.DatabaseContentionMetrics;
import com.example.coffeeordersystem.common.observability.RequestCorrelation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private final DatabaseContentionMetrics contentionMetrics;

  public GlobalExceptionHandler(DatabaseContentionMetrics contentionMetrics) {
    this.contentionMetrics = contentionMetrics;
  }

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException exception) {
    return errorResponse(exception.errorCode());
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType(
      HttpMediaTypeNotSupportedException exception) {
    return errorResponse(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
  }

  @ExceptionHandler({MethodArgumentNotValidException.class, MissingRequestHeaderException.class})
  public ResponseEntity<ApiResponse<Void>> handleInvalidRequest(Exception exception) {
    return errorResponse(ErrorCode.INVALID_REQUEST);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<Void>> handleUnreadableRequest(
      HttpMessageNotReadableException exception) {
    ErrorCode errorCode =
        targetsFloatingPointField(exception, "amount")
            ? ErrorCode.INVALID_CHARGE_AMOUNT
            : ErrorCode.INVALID_REQUEST;
    return errorResponse(errorCode);
  }

  @ExceptionHandler({PessimisticLockingFailureException.class, QueryTimeoutException.class})
  public ResponseEntity<ApiResponse<Void>> handleDatabaseContention(Exception exception) {
    String contentionType = contentionMetrics.record(exception);
    log.atWarn()
        .addKeyValue("requestId", RequestCorrelation.requestId())
        .addKeyValue("contentionType", contentionType)
        .addKeyValue("errorType", exception.getClass().getSimpleName())
        .log("DB 경합으로 요청 롤백");
    return errorResponse(ErrorCode.TEMPORARILY_UNAVAILABLE);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<Void> handleNoResourceFound(NoResourceFoundException exception) {
    return ResponseEntity.notFound().build();
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
    log.atError()
        .addKeyValue("requestId", RequestCorrelation.requestId())
        .addKeyValue("errorType", exception.getClass().getSimpleName())
        .log("예상하지 못한 API 오류");
    return errorResponse(ErrorCode.INTERNAL_SERVER_ERROR);
  }

  private ResponseEntity<ApiResponse<Void>> errorResponse(ErrorCode errorCode) {
    return ResponseEntity.status(errorCode.httpStatus())
        .body(ApiResponse.failure(errorCode.name(), errorCode.message()));
  }

  private boolean targetsFloatingPointField(Throwable exception, String fieldName) {
    Throwable current = exception;
    while (current != null) {
      if (current instanceof MismatchedInputException mismatchedInputException
          && !(mismatchedInputException instanceof UnrecognizedPropertyException)
          && mismatchedInputException.getCurrentToken() == JsonToken.VALUE_NUMBER_FLOAT
          && mismatchedInputException.getPath().stream()
              .anyMatch(reference -> fieldName.equals(reference.getPropertyName()))) {
        return true;
      }
      if (current == current.getCause()) {
        return false;
      }
      current = current.getCause();
    }
    return false;
  }
}
