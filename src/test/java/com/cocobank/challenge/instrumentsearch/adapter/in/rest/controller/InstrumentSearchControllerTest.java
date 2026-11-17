package com.cocobank.challenge.instrumentsearch.adapter.in.rest.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cocobank.challenge.instrumentsearch.application.exception.InstrumentSearchNotFoundException;
import com.cocobank.challenge.instrumentsearch.application.usecase.SearchInstrumentsUseCase;
import com.cocobank.challenge.instrumentsearch.domain.InstrumentSearchField;
import com.cocobank.challenge.instrumentsearch.domain.InstrumentSearchResult;
import com.cocobank.challenge.portfolio.adapter.in.rest.controller.PortfolioExceptionHandler;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@ExtendWith(MockitoExtension.class)
class InstrumentSearchControllerTest {

  @Mock private SearchInstrumentsUseCase useCase;
  private MockMvc mockMvc;
  private LocalValidatorFactoryBean validator;

  @BeforeEach
  void setUp() {
    validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    mockMvc =
        MockMvcBuilders.standaloneSetup(new InstrumentSearchController(useCase))
            .setValidator(validator)
            .setControllerAdvice(
                new InstrumentSearchExceptionHandler(), new PortfolioExceptionHandler())
            .build();
  }

  @AfterEach
  void tearDown() {
    validator.close();
  }

  @Test
  void returnsAllFieldsIncludingNullPricesWithoutAuthentication() throws Exception {
    when(useCase.execute("abc", InstrumentSearchField.TICKER))
        .thenReturn(
            List.of(
                new InstrumentSearchResult(
                    1,
                    "ABC",
                    "First",
                    "ACCIONES",
                    new BigDecimal("12.50"),
                    LocalDate.of(2024, 1, 2)),
                new InstrumentSearchResult(2, "ABCD", "Second", "ACCIONES", null, null)));

    mockMvc
        .perform(get("/api/v1/instruments").param("query", "abc").param("field", "ticker"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(1))
        .andExpect(jsonPath("$[0].ticker").value("ABC"))
        .andExpect(jsonPath("$[0].name").value("First"))
        .andExpect(jsonPath("$[0].type").value("ACCIONES"))
        .andExpect(jsonPath("$[0].marketPrice").value(12.50))
        .andExpect(jsonPath("$[0].closeDate").value("2024-01-02"))
        .andExpect(jsonPath("$[1].marketPrice").value(nullValue()))
        .andExpect(jsonPath("$[1].closeDate").value(nullValue()));
    verify(useCase).execute("abc", InstrumentSearchField.TICKER);
  }

  @Test
  void delegatesNameSearch() throws Exception {
    when(useCase.execute("  banco ", InstrumentSearchField.NAME))
        .thenReturn(List.of(new InstrumentSearchResult(1, "AAA", "Banco", "ACCIONES", null, null)));

    mockMvc
        .perform(get("/api/v1/instruments").param("query", "  banco ").param("field", "name"))
        .andExpect(status().isOk());
    verify(useCase).execute("  banco ", InstrumentSearchField.NAME);
  }

  @Test
  void rejectsInvalidParametersWithoutCallingUseCase() throws Exception {
    mockMvc
        .perform(get("/api/v1/instruments").param("field", "ticker"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/v1/instruments").param("query", "abc"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/v1/instruments").param("query", " \t\u2003").param("field", "ticker"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/v1/instruments").param("query", "").param("field", "name"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/v1/instruments").param("query", "abc").param("field", "Ticker"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/v1/instruments").param("query", "abc").param("field", " ticker "))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/v1/instruments").param("query", "abc").param("field", "other"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(useCase);
  }

  @Test
  void mapsNoMatchesToProblemDetail404() throws Exception {
    when(useCase.execute("missing", InstrumentSearchField.TICKER))
        .thenThrow(new InstrumentSearchNotFoundException());

    mockMvc
        .perform(get("/api/v1/instruments").param("query", "missing").param("field", "ticker"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.title").value("No encontrado"))
        .andExpect(jsonPath("$.detail").value("No se encontraron instrumentos."));
    verify(useCase).execute("missing", InstrumentSearchField.TICKER);
  }

  @Test
  void rejectsUnsupportedHttpMethodWithoutCallingUseCase() throws Exception {
    mockMvc.perform(post("/api/v1/instruments")).andExpect(status().isMethodNotAllowed());
    verifyNoInteractions(useCase);
  }
}
