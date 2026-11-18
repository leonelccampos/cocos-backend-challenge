package com.cocobank.challenge.adapter.in.rest.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestCorrelationFilterTest {

  private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

  @Test
  void generatesRequestIdAndIgnoresClientProvidedValue() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, "client-value");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<String> requestIdInContext = new AtomicReference<>();
    MDC.put(RequestCorrelationFilter.REQUEST_ID_MDC_KEY, "parent-request");

    try {
      filter.doFilter(
          request,
          response,
          (servletRequest, servletResponse) ->
              requestIdInContext.set(MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY)));

      String requestId = response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER);
      assertThat(requestId).isNotBlank().isNotEqualTo("client-value");
      assertThat(requestIdInContext.get()).isEqualTo(requestId);
      assertThat(MDC.get(RequestCorrelationFilter.REQUEST_ID_MDC_KEY)).isEqualTo("parent-request");
    } finally {
      MDC.remove(RequestCorrelationFilter.REQUEST_ID_MDC_KEY);
    }
  }
}
