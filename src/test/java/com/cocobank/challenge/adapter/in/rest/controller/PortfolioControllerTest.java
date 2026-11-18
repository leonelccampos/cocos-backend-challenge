package com.cocobank.challenge.adapter.in.rest.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cocobank.challenge.adapter.in.rest.filter.RequestCorrelationFilter;
import com.cocobank.challenge.adapter.in.rest.handler.PortfolioExceptionHandler;
import com.cocobank.challenge.application.exception.PortfolioNotFoundException;
import com.cocobank.challenge.application.usecase.GetPortfolioUseCase;
import com.cocobank.challenge.domain.portfolio.InvalidPortfolioDataException;
import com.cocobank.challenge.domain.portfolio.Portfolio;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;

@ExtendWith(MockitoExtension.class)
class PortfolioControllerTest {

  @Mock private GetPortfolioUseCase getPortfolioUseCase;

  private MockMvc mockMvc;
  private LocalValidatorFactoryBean validator;

  @BeforeEach
  void setUp() {
    validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    ProxyFactory proxyFactory = new ProxyFactory(new PortfolioController(getPortfolioUseCase));
    proxyFactory.setProxyTargetClass(true);
    proxyFactory.addAdvice(new MethodValidationInterceptor(validator.getValidator()));
    mockMvc =
        MockMvcBuilders.standaloneSetup(proxyFactory.getProxy())
            .setValidator(validator)
            .setControllerAdvice(new PortfolioExceptionHandler())
            .addFilters(new RequestCorrelationFilter())
            .build();
  }

  @AfterEach
  void tearDown() {
    validator.close();
  }

  @Test
  void delegatesValidRequestToUseCase() throws Exception {
    Portfolio portfolio =
        new Portfolio(
            BigDecimal.valueOf(100), BigDecimal.valueOf(25), BigDecimal.valueOf(125), List.of());
    when(getPortfolioUseCase.execute(7, "ACCT-007")).thenReturn(portfolio);

    mockMvc
        .perform(get("/api/v1/users/7/portfolio").param("accountNumber", "ACCT-007"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.availableCash").value(100))
        .andExpect(jsonPath("$.totalAccountValue").value(125))
        .andExpect(header().exists("X-Request-Id"))
        .andExpect(jsonPath("$.reservedCash").doesNotExist());

    verify(getPortfolioUseCase).execute(7, "ACCT-007");
  }

  @Test
  void rejectsInvalidPathIdAndAccountNumber() throws Exception {
    mockMvc
        .perform(get("/api/v1/users/not-an-id/portfolio").param("accountNumber", "10001"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/v1/users/0/portfolio").param("accountNumber", "10001"))
        .andExpect(status().isBadRequest());
    mockMvc.perform(get("/api/v1/users/7/portfolio")).andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/v1/users/7/portfolio").param("accountNumber", "A".repeat(21)))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(getPortfolioUseCase);
  }

  @Test
  void returnsNotFoundWhenUserIdPathSegmentIsMissing() throws Exception {
    mockMvc.perform(get("/api/v1/users/portfolio")).andExpect(status().isNotFound());

    verifyNoInteractions(getPortfolioUseCase);
  }

  @Test
  void mapsMissingPortfolioToNotFound() throws Exception {
    when(getPortfolioUseCase.execute(7, "10001")).thenThrow(new PortfolioNotFoundException());

    mockMvc
        .perform(get("/api/v1/users/7/portfolio").param("accountNumber", "10001"))
        .andExpect(status().isNotFound());
  }

  @Test
  void hidesDomainErrorDetailsBehindGenericServerError() throws Exception {
    when(getPortfolioUseCase.execute(7, "10001"))
        .thenThrow(new InvalidPortfolioDataException("sensitive internal detail"));

    mockMvc
        .perform(get("/api/v1/users/7/portfolio").param("accountNumber", "10001"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.detail").value("No fue posible procesar la solicitud."))
        .andExpect(content().string(not(containsString("sensitive internal detail"))));
  }

  @Test
  void hidesPersistenceErrorDetailsBehindGenericServerError() throws Exception {
    when(getPortfolioUseCase.execute(7, "10001"))
        .thenThrow(new DataAccessResourceFailureException("database secret"));

    mockMvc
        .perform(get("/api/v1/users/7/portfolio").param("accountNumber", "10001"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.detail").value("No fue posible procesar la solicitud."))
        .andExpect(content().string(not(containsString("database secret"))));
  }

  @Test
  void hidesTransactionErrorDetailsBehindGenericServerError() throws Exception {
    when(getPortfolioUseCase.execute(7, "10001"))
        .thenThrow(new TransactionSystemException("transaction secret"));

    mockMvc
        .perform(get("/api/v1/users/7/portfolio").param("accountNumber", "10001"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.detail").value("No fue posible procesar la solicitud."))
        .andExpect(content().string(not(containsString("transaction secret"))));
  }

  @Test
  void logsOnlyAggregatedSuccessDetails() throws Exception {
    Portfolio portfolio =
        new Portfolio(
            BigDecimal.valueOf(100), BigDecimal.valueOf(25), BigDecimal.valueOf(125), List.of());
    when(getPortfolioUseCase.execute(7, "ACCT-007")).thenReturn(portfolio);
    Logger logger = (Logger) LoggerFactory.getLogger(PortfolioController.class);
    Level originalLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.setLevel(Level.INFO);
    logger.addAppender(appender);
    MvcResult result;

    try {
      result =
          mockMvc
              .perform(get("/api/v1/users/7/portfolio").param("accountNumber", "ACCT-007"))
              .andExpect(status().isOk())
              .andReturn();
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(originalLevel);
      appender.stop();
    }

    assertThat(appender.list)
        .singleElement()
        .satisfies(event -> {
          assertThat(event.getLevel()).isEqualTo(Level.INFO);
          assertThat(event.getFormattedMessage())
              .contains("http_status=200", "instrument_count=0", "unavailable_valuation_count=0");
          assertThat(event.getFormattedMessage())
              .doesNotContain("ACCT-007", "userId=7", "100", "125");
          assertThat(event.getMDCPropertyMap())
              .containsKey("request_id")
              .extractingByKey("request_id")
              .isEqualTo(result.getResponse().getHeader("X-Request-Id"));
        });
  }
}
