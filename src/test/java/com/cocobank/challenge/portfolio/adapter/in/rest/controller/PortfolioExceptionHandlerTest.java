package com.cocobank.challenge.portfolio.adapter.in.rest.controller;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cocobank.challenge.portfolio.application.exception.PortfolioNotFoundException;
import com.cocobank.challenge.portfolio.domain.InvalidPortfolioDataException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

class PortfolioExceptionHandlerTest {

  private final PortfolioExceptionHandler handler = new PortfolioExceptionHandler();

  @Test
  void mapsMissingPortfolioToGenericNotFoundResponse() {
    ResponseEntity<ProblemDetail> response =
        handler.handleNotFound(new PortfolioNotFoundException());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().getDetail()).isEqualTo("No se encontró el portfolio solicitado.");
  }

  @Test
  void mapsKnownInternalErrorsToGenericServerErrorResponse() {
    ResponseEntity<ProblemDetail> response =
        handler.handleInternalError(new InvalidPortfolioDataException("private details"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody().getDetail()).isEqualTo("No fue posible procesar la solicitud.");
  }

  @Test
  void mapsInvalidRequestsToGenericBadRequestResponse() {
    ResponseEntity<ProblemDetail> response = handler.handleInvalidRequest();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().getDetail()).isEqualTo("Revise los parámetros enviados.");
  }

  @Test
  void logsNotFoundAtInfoWithoutRequestIdentifiers() {
    Logger logger = (Logger) LoggerFactory.getLogger(PortfolioExceptionHandler.class);
    Level originalLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.setLevel(Level.INFO);
    logger.addAppender(appender);

    try {
      handler.handleNotFound(new PortfolioNotFoundException());
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(originalLevel);
      appender.stop();
    }

    assertThat(appender.list)
        .singleElement()
        .satisfies(event -> {
          assertThat(event.getLevel()).isEqualTo(Level.INFO);
          assertThat(event.getFormattedMessage()).contains("http_status=404");
          assertThat(event.getFormattedMessage())
              .doesNotContain("user_id", "account_number", "10001");
        });
  }

  @Test
  void logsKnownInternalFailuresAtErrorWithTheirException() {
    Logger logger = (Logger) LoggerFactory.getLogger(PortfolioExceptionHandler.class);
    Level originalLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.setLevel(Level.ERROR);
    logger.addAppender(appender);

    try {
      handler.handleInternalError(new InvalidPortfolioDataException("private details"));
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(originalLevel);
      appender.stop();
    }

    assertThat(appender.list)
        .singleElement()
        .satisfies(event -> {
          assertThat(event.getLevel()).isEqualTo(Level.ERROR);
          assertThat(event.getFormattedMessage()).contains("http_status=500");
          assertThat(event.getThrowableProxy()).isNotNull();
        });
  }
}
