package com.cocobank.challenge.portfolio.adapter.in.rest.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public final class RequestCorrelationFilter extends OncePerRequestFilter {

  static final String REQUEST_ID_HEADER = "X-Request-Id";
  static final String REQUEST_ID_MDC_KEY = "request_id";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String requestId = UUID.randomUUID().toString();
    String previousRequestId = MDC.get(REQUEST_ID_MDC_KEY);
    MDC.put(REQUEST_ID_MDC_KEY, requestId);
    response.setHeader(REQUEST_ID_HEADER, requestId);

    try {
      filterChain.doFilter(request, response);
    } finally {
      if (previousRequestId == null) {
        MDC.remove(REQUEST_ID_MDC_KEY);
      } else {
        MDC.put(REQUEST_ID_MDC_KEY, previousRequestId);
      }
    }
  }
}
