package com.example.coffeeordersystem.common.observability;

import org.slf4j.MDC;

public final class RequestCorrelation {

  static final String REQUEST_ID_KEY = "requestId";

  private RequestCorrelation() {}

  public static String requestId() {
    String requestId = MDC.get(REQUEST_ID_KEY);
    return requestId == null ? "none" : requestId;
  }
}
