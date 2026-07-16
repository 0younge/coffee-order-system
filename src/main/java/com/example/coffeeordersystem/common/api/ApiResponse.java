package com.example.coffeeordersystem.common.api;

public record ApiResponse<T>(boolean success, String code, String message, T data) {

  public static <T> ApiResponse<T> success(String code, String message, T data) {
    return new ApiResponse<>(true, code, message, data);
  }

  public static ApiResponse<Void> failure(String code, String message) {
    return new ApiResponse<>(false, code, message, null);
  }
}
