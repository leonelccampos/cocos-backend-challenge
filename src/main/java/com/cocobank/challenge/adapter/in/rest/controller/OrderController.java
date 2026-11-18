package com.cocobank.challenge.adapter.in.rest.controller;

import com.cocobank.challenge.adapter.in.rest.representation.CancelOrderRequest;
import com.cocobank.challenge.adapter.in.rest.representation.OrderResponse;
import com.cocobank.challenge.adapter.in.rest.representation.SubmitOrderRequest;
import com.cocobank.challenge.application.dto.CancelOrderCommand;
import com.cocobank.challenge.application.dto.OrderSubmissionResult;
import com.cocobank.challenge.application.dto.SubmitOrderCommand;
import com.cocobank.challenge.application.exception.OrderBadRequestException;
import com.cocobank.challenge.application.usecase.CancelOrderUseCase;
import com.cocobank.challenge.application.usecase.SubmitOrderUseCase;
import com.cocobank.challenge.domain.order.Order;
import com.cocobank.challenge.domain.order.OrderRejectionReason;
import com.cocobank.challenge.domain.order.OrderSide;
import com.cocobank.challenge.domain.order.OrderType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Validated
public class OrderController {

  private static final Logger LOGGER = LoggerFactory.getLogger(OrderController.class);

  private final SubmitOrderUseCase submitOrderUseCase;
  private final CancelOrderUseCase cancelOrderUseCase;

  public OrderController(
      SubmitOrderUseCase submitOrderUseCase, CancelOrderUseCase cancelOrderUseCase) {
    this.submitOrderUseCase = submitOrderUseCase;
    this.cancelOrderUseCase = cancelOrderUseCase;
  }

  @PostMapping("/{userId}/orders")
  @Operation(summary = "Enviar una orden de compra o venta")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Orden aceptada"),
    @ApiResponse(responseCode = "400", description = "Solicitud inválida"),
    @ApiResponse(responseCode = "404", description = "Usuario o instrumento no encontrados"),
    @ApiResponse(responseCode = "409", description = "Clave de idempotencia reutilizada"),
    @ApiResponse(responseCode = "422", description = "Orden válida rechazada")
  })
  public ResponseEntity<?> submitOrder(
      @PathVariable @Positive int userId,
      @RequestHeader("Idempotency-Key") @Pattern(regexp = "[\\x21-\\x7E]{1,128}") String key,
      @Valid @RequestBody SubmitOrderRequest request) {

    OrderSubmissionResult result =
        submitOrderUseCase.execute(
            new SubmitOrderCommand(
                userId,
                request.instrumentId(),
                parseSide(request.side()),
                parseType(request.type()),
                request.size(),
                request.amount(),
                request.price(),
                key));
    OrderResponse order = OrderResponse.from(result.order());
    if (result.isRejected()) {
      LOGGER.info(
          "orders.request.completed operation=CREATE_ORDER http_status=422 rejection_reason={}",
          result.rejectionReason());
      return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(rejected(result, order));
    }
    LOGGER.info("orders.request.completed operation=CREATE_ORDER http_status=201");
    return ResponseEntity.status(HttpStatus.CREATED).body(order);
  }

  @PatchMapping("/{userId}/orders/{orderId}")
  @Operation(summary = "Cancelar una orden pendiente")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Orden cancelada"),
    @ApiResponse(responseCode = "400", description = "Solicitud inválida"),
    @ApiResponse(responseCode = "404", description = "Usuario u orden no encontrados"),
    @ApiResponse(responseCode = "409", description = "La orden no puede cancelarse")
  })
  public ResponseEntity<OrderResponse> cancelOrder(
      @PathVariable @Positive int userId,
      @PathVariable @Positive int orderId,
      @Valid @RequestBody CancelOrderRequest request) {

    Order order =
        cancelOrderUseCase.execute(new CancelOrderCommand(userId, orderId, request.status()));
    LOGGER.info("orders.request.completed operation=CANCEL_ORDER http_status=200");
    return ResponseEntity.ok(OrderResponse.from(order));
  }

  private ProblemDetail rejected(OrderSubmissionResult result, OrderResponse order) {
    OrderRejectionReason reason = result.rejectionReason();
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.UNPROCESSABLE_CONTENT, rejectionDescription(reason));
    problem.setTitle("Orden rechazada");
    problem.setProperty("reason", reason.name());
    problem.setProperty("order", order);
    return problem;
  }

  private static String rejectionDescription(OrderRejectionReason reason) {
    return switch (reason) {
      case INSUFFICIENT_CASH -> "Fondos insuficientes para enviar la orden.";
      case INSUFFICIENT_SHARES -> "Acciones disponibles insuficientes para enviar la orden.";
      case MARKET_PRICE_UNAVAILABLE -> "No existe un precio de mercado utilizable para la orden.";
    };
  }

  private static OrderSide parseSide(String value) {
    try {
      return OrderSide.valueOf(value);
    } catch (IllegalArgumentException | NullPointerException exception) {
      throw new OrderBadRequestException("side debe ser BUY o SELL.");
    }
  }

  private static OrderType parseType(String value) {
    try {
      return OrderType.valueOf(value);
    } catch (IllegalArgumentException | NullPointerException exception) {
      throw new OrderBadRequestException("type debe ser MARKET o LIMIT.");
    }
  }
}
