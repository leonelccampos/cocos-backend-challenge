package com.cocobank.challenge.portfolio.adapter.in.rest.representation;

import com.cocobank.challenge.portfolio.domain.Portfolio;
import com.cocobank.challenge.portfolio.domain.PortfolioInstrument;
import com.cocobank.challenge.portfolio.domain.ValuationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

public record PortfolioResponse(
    BigDecimal availableCash, BigDecimal totalAccountValue, List<InstrumentResponse> instruments) {

  public PortfolioResponse {
    instruments = List.copyOf(instruments);
  }

  public static PortfolioResponse from(Portfolio portfolio) {
    return new PortfolioResponse(
        portfolio.availableCash(),
        portfolio.totalAccountValue(),
        portfolio.instruments().stream().map(InstrumentResponse::from).toList());
  }

  public record InstrumentResponse(
      int instrumentId,
      String ticker,
      String name,
      String type,
      int quantity,
      BigDecimal marketValue,
      BigDecimal accumulatedReturn,
      BigDecimal performance,
      @Schema(
              description =
                  "Variación diaria estimada en pesos; null si no puede calcularse. "
                      + "La fecha de la orden se usa como aproximación de la ejecución.",
              nullable = true)
          BigDecimal dailyReturn,
      ValuationStatus valuationStatus) {

    private static InstrumentResponse from(PortfolioInstrument instrument) {
      return new InstrumentResponse(
          instrument.instrumentId(),
          instrument.ticker(),
          instrument.name(),
          instrument.type(),
          instrument.quantity(),
          instrument.marketValue(),
          instrument.accumulatedReturn(),
          instrument.accumulatedReturnPercentage(),
          instrument.dailyReturn(),
          instrument.valuationStatus());
    }
  }
}
