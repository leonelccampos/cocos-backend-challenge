package com.cocobank.challenge.domain.portfolio;

import java.math.BigDecimal;

public record PortfolioValuation(BigDecimal marketValue, ValuationStatus status) {

  public PortfolioValuation {
    if (status == null) {
      throw new NullPointerException("status");
    }
    if (status == ValuationStatus.AVAILABLE && marketValue == null) {
      throw new IllegalArgumentException("available valuation requires marketValue");
    }
    if (status == ValuationStatus.UNAVAILABLE && marketValue != null) {
      throw new IllegalArgumentException("unavailable valuation must not contain marketValue");
    }
  }

  public static PortfolioValuation calculate(int quantity, BigDecimal close) {
    if (quantity == 0) {
      throw new IllegalArgumentException("quantity must not be zero");
    }
    if (close == null || close.signum() <= 0) {
      return new PortfolioValuation(null, ValuationStatus.UNAVAILABLE);
    }
    return new PortfolioValuation(
        close.multiply(BigDecimal.valueOf(quantity)), ValuationStatus.AVAILABLE);
  }
}
