package com.cocobank.challenge.adapter.in.rest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cocobank.challenge.adapter.in.rest.handler.OrderExceptionHandler;
import com.cocobank.challenge.application.dto.OrderSubmissionResult;
import com.cocobank.challenge.application.exception.IdempotencyConflictException;
import com.cocobank.challenge.application.usecase.SubmitOrderUseCase;
import com.cocobank.challenge.domain.order.Order;
import com.cocobank.challenge.domain.order.OrderRejectionReason;
import com.cocobank.challenge.domain.order.OrderSide;
import com.cocobank.challenge.domain.order.OrderStatus;
import com.cocobank.challenge.domain.order.OrderType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

  @Mock private SubmitOrderUseCase useCase;

  private MockMvc mockMvc;
  private LocalValidatorFactoryBean validator;

  @BeforeEach
  void setUp() {
    validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    ProxyFactory proxy = new ProxyFactory(new OrderController(useCase));
    proxy.setProxyTargetClass(true);
    proxy.addAdvice(new MethodValidationInterceptor(validator.getValidator()));
    mockMvc =
        MockMvcBuilders.standaloneSetup(proxy.getProxy())
            .setValidator(validator)
            .setControllerAdvice(new OrderExceptionHandler())
            .build();
  }

  @AfterEach
  void tearDown() {
    validator.close();
  }

  @Test
  void returnsCreatedOrder() throws Exception {
    Order order = order(OrderStatus.NEW);
    when(useCase.execute(any())).thenReturn(OrderSubmissionResult.accepted(order));

    mockMvc
        .perform(
            post("/api/v1/users/1/orders")
                .header("Idempotency-Key", "request-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"instrumentId":31,"side":"BUY","type":"LIMIT","size":2,"price":10.50}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(41))
        .andExpect(jsonPath("$.status").value("NEW"))
        .andExpect(jsonPath("$.amount").doesNotExist());
  }

  @Test
  void returnsProblemDetailWithPersistedRejectedOrder() throws Exception {
    when(useCase.execute(any()))
        .thenReturn(
            OrderSubmissionResult.rejected(
                order(OrderStatus.REJECTED), OrderRejectionReason.INSUFFICIENT_CASH));

    mockMvc
        .perform(
            post("/api/v1/users/1/orders")
                .header("Idempotency-Key", "request-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"instrumentId":31,"side":"BUY","type":"LIMIT","size":2,"price":10.50}
                    """))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.reason").value("INSUFFICIENT_CASH"))
        .andExpect(jsonPath("$.order.status").value("REJECTED"))
        .andExpect(jsonPath("$.detail").value("Fondos insuficientes para enviar la orden."));
  }

  @Test
  void rejectsMissingAndInvalidIdempotencyKeys() throws Exception {
    String body =
        """
        {"instrumentId":31,"side":"BUY","type":"MARKET","size":2}
        """;

    mockMvc
        .perform(
            post("/api/v1/users/1/orders").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            post("/api/v1/users/1/orders")
                .header("Idempotency-Key", "with spaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(useCase);
  }

  @Test
  void mapsIdempotencyConflict() throws Exception {
    when(useCase.execute(any())).thenThrow(new IdempotencyConflictException());

    mockMvc
        .perform(
            post("/api/v1/users/1/orders")
                .header("Idempotency-Key", "request-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"instrumentId":31,"side":"BUY","type":"MARKET","size":2}
                    """))
        .andExpect(status().isConflict());
  }

  private Order order(OrderStatus status) {
    return new Order(
        41,
        1,
        31,
        OrderSide.BUY,
        OrderType.LIMIT,
        status == OrderStatus.REJECTED ? null : 2,
        status == OrderStatus.REJECTED ? null : new BigDecimal("10.50"),
        status,
        LocalDateTime.parse("2026-01-02T12:00:00"));
  }
}
