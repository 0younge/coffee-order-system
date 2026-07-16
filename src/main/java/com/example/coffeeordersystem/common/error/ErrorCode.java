package com.example.coffeeordersystem.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
  INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다."),
  INVALID_CHARGE_AMOUNT(HttpStatus.BAD_REQUEST, "충전 금액이 올바르지 않습니다."),
  UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 미디어 타입입니다."),
  RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 경로를 찾을 수 없습니다."),
  USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
  MENU_NOT_FOUND(HttpStatus.NOT_FOUND, "메뉴를 찾을 수 없습니다."),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
  INSUFFICIENT_POINT(HttpStatus.CONFLICT, "포인트 잔액이 부족합니다."),
  POINT_BALANCE_OVERFLOW(HttpStatus.CONFLICT, "포인트 잔액 범위를 초과합니다."),
  IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "같은 멱등키를 다른 요청에 사용할 수 없습니다."),
  INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),
  TEMPORARILY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "일시적으로 요청을 처리할 수 없습니다.");

  private final HttpStatus httpStatus;
  private final String message;

  ErrorCode(HttpStatus httpStatus, String message) {
    this.httpStatus = httpStatus;
    this.message = message;
  }

  public HttpStatus httpStatus() {
    return httpStatus;
  }

  public String message() {
    return message;
  }
}
