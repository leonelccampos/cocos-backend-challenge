package com.cocobank.challenge.adapter.in.rest.controller;

import com.cocobank.challenge.adapter.in.rest.representation.PortfolioRequest;
import com.cocobank.challenge.adapter.in.rest.representation.PortfolioResponse;
import com.cocobank.challenge.application.usecase.GetPortfolioUseCase;
import com.cocobank.challenge.domain.portfolio.Portfolio;
import com.cocobank.challenge.domain.portfolio.ValuationStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Validated
public class PortfolioController {

  private static final Logger LOGGER = LoggerFactory.getLogger(PortfolioController.class);

  private final GetPortfolioUseCase getPortfolioUseCase;

  public PortfolioController(GetPortfolioUseCase getPortfolioUseCase) {
    this.getPortfolioUseCase = getPortfolioUseCase;
  }

  @GetMapping("/{userId}/portfolio")
  @Operation(summary = "Consultar el portfolio de una cuenta")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Portfolio consultado"),
    @ApiResponse(responseCode = "400", description = "Solicitud inválida"),
    @ApiResponse(responseCode = "404", description = "Usuario o cuenta no encontrados"),
    @ApiResponse(responseCode = "500", description = "Error interno")
  })
  public PortfolioResponse getPortfolio(
      @PathVariable @Positive int userId, @Valid @ModelAttribute PortfolioRequest request) {
    Portfolio portfolio = getPortfolioUseCase.execute(userId, request.accountNumber());
    PortfolioResponse response = PortfolioResponse.from(portfolio);
    logSuccessfulRequest(portfolio);
    return response;
  }

  private void logSuccessfulRequest(Portfolio portfolio) {
    long unavailableValuations =
        portfolio.instruments().stream()
            .filter(instrument -> instrument.valuationStatus() == ValuationStatus.UNAVAILABLE)
            .count();
    LOGGER.info(
        "portfolio.request.completed operation=GET_PORTFOLIO http_status=200 instrument_count={} unavailable_valuation_count={}",
        portfolio.instruments().size(),
        unavailableValuations);
  }
}
