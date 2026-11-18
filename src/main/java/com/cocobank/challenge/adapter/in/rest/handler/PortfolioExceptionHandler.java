package com.cocobank.challenge.adapter.in.rest.handler;

import com.cocobank.challenge.adapter.in.rest.controller.PortfolioController;
import com.cocobank.challenge.application.exception.PortfolioNotFoundException;
import com.cocobank.challenge.domain.order.InvalidOrderDataException;
import com.cocobank.challenge.domain.portfolio.InvalidPortfolioDataException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = PortfolioController.class)
public class PortfolioExceptionHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(PortfolioExceptionHandler.class);

  @ExceptionHandler(PortfolioNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleNotFound(PortfolioNotFoundException exception) {
    LOGGER.info("portfolio.request.completed operation=GET_PORTFOLIO http_status=404");
    return response(
        HttpStatus.NOT_FOUND, "No encontrado", "No se encontró el portfolio solicitado.");
  }

  @ExceptionHandler({
    InvalidPortfolioDataException.class,
    InvalidOrderDataException.class,
    DataAccessException.class,
    TransactionException.class
  })
  public ResponseEntity<ProblemDetail> handleInternalError(RuntimeException exception) {
    LOGGER.error(
        "portfolio.request.failed operation=GET_PORTFOLIO http_status=500 error_type={}",
        exception.getClass().getSimpleName(),
        exception);
    return response(
        HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", "No fue posible procesar la solicitud.");
  }

  @ExceptionHandler({
    ConstraintViolationException.class,
    BindException.class,
    MethodArgumentTypeMismatchException.class,
    MissingServletRequestParameterException.class
  })
  public ResponseEntity<ProblemDetail> handleInvalidRequest() {
    return response(
        HttpStatus.BAD_REQUEST, "Solicitud inválida", "Revise los parámetros enviados.");
  }

  private ResponseEntity<ProblemDetail> response(HttpStatus status, String title, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(title);
    return ResponseEntity.status(status).body(problem);
  }
}
