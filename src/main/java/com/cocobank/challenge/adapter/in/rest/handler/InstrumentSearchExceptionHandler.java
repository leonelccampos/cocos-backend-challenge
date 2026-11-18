package com.cocobank.challenge.adapter.in.rest.handler;

import com.cocobank.challenge.adapter.in.rest.controller.InstrumentSearchController;
import com.cocobank.challenge.application.exception.InstrumentSearchNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = InstrumentSearchController.class)
public class InstrumentSearchExceptionHandler {

  @ExceptionHandler(InstrumentSearchNotFoundException.class)
  public ProblemDetail handleNotFound() {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "No se encontraron instrumentos.");
    problem.setTitle("No encontrado");
    return problem;
  }
}
