package com.cocobank.challenge.adapter.in.rest.handler;

import com.cocobank.challenge.adapter.in.rest.controller.OrderController;
import com.cocobank.challenge.application.exception.IdempotencyConflictException;
import com.cocobank.challenge.application.exception.OrderBadRequestException;
import com.cocobank.challenge.application.exception.OrderNotFoundException;
import com.cocobank.challenge.domain.order.OrderNotCancellableException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = OrderController.class)
public class OrderExceptionHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(OrderExceptionHandler.class);

  @ExceptionHandler(OrderBadRequestException.class)
  public ResponseEntity<ProblemDetail> badRequest(OrderBadRequestException exception) {
    return response(HttpStatus.BAD_REQUEST, "Solicitud inválida", exception.getMessage());
  }

  @ExceptionHandler(OrderNotFoundException.class)
  public ResponseEntity<ProblemDetail> notFound(OrderNotFoundException exception) {
    return response(HttpStatus.NOT_FOUND, "No encontrado", exception.getMessage());
  }

  @ExceptionHandler(IdempotencyConflictException.class)
  public ResponseEntity<ProblemDetail> conflict(IdempotencyConflictException exception) {
    return response(HttpStatus.CONFLICT, "Conflicto de idempotencia", exception.getMessage());
  }

  @ExceptionHandler(OrderNotCancellableException.class)
  public ResponseEntity<ProblemDetail> notCancellable(OrderNotCancellableException exception) {
    return response(HttpStatus.CONFLICT, "Orden no cancelable", exception.getMessage());
  }

  @ExceptionHandler({
    ConstraintViolationException.class,
    MethodArgumentNotValidException.class,
    MethodArgumentTypeMismatchException.class,
    MissingRequestHeaderException.class,
    HttpMessageNotReadableException.class
  })
  public ResponseEntity<ProblemDetail> invalidInput() {
    return response(
        HttpStatus.BAD_REQUEST,
        "Solicitud inválida",
        "Revise el cuerpo, la ruta y los encabezados.");
  }

  @ExceptionHandler({
    DataAccessException.class,
    TransactionException.class,
    IllegalStateException.class
  })
  public ResponseEntity<ProblemDetail> internalError(RuntimeException exception) {
    LOGGER.error(
        "orders.request.failed operation=CREATE_ORDER http_status=500 error_type={}",
        exception.getClass().getSimpleName(),
        exception);
    return response(
        HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", "No fue posible procesar la orden.");
  }

  private ResponseEntity<ProblemDetail> response(HttpStatus status, String title, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(title);
    return ResponseEntity.status(status).body(problem);
  }
}
