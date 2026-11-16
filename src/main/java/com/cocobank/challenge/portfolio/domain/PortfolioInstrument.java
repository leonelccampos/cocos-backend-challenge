package com.cocobank.challenge.portfolio.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record PortfolioInstrument(
    int instrumentId,
    String ticker,
    String name,
    String type,
    int quantity,
    BigDecimal marketValue,
    BigDecimal accumulatedReturn,
    BigDecimal accumulatedReturnPercentage,
    BigDecimal dailyReturn,
    ValuationStatus valuationStatus) {

  public PortfolioInstrument {
    if (instrumentId <= 0) {
      throw new IllegalArgumentException("instrumentId must be positive");
    }
    Objects.requireNonNull(ticker, "ticker");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(type, "type");
    if (quantity == 0) {
      throw new IllegalArgumentException("quantity must not be zero");
    }
    Objects.requireNonNull(valuationStatus, "valuationStatus");

    if (valuationStatus == ValuationStatus.AVAILABLE) {
      Objects.requireNonNull(marketValue, "marketValue");
      Objects.requireNonNull(accumulatedReturn, "accumulatedReturn");
      if (marketValue.signum() != Integer.signum(quantity)) {
        throw new IllegalArgumentException("marketValue must have the same sign as quantity");
      }
    } else if (marketValue != null
        || accumulatedReturn != null
        || accumulatedReturnPercentage != null
        || dailyReturn != null) {
      throw new IllegalArgumentException("unavailable valuation must not contain monetary values");
    }
  }
}
