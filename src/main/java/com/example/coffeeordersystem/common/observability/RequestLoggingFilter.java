package com.example.coffeeordersystem.common.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
class RequestLoggingFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String requestId = UUID.randomUUID().toString();
    MDC.put(RequestCorrelation.REQUEST_ID_KEY, requestId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      log.atInfo()
          .addKeyValue("requestId", requestId)
          .addKeyValue("method", request.getMethod())
          .addKeyValue("path", request.getRequestURI())
          .addKeyValue("status", response.getStatus())
          .log("HTTP 요청 완료");
      MDC.remove(RequestCorrelation.REQUEST_ID_KEY);
    }
  }
}
