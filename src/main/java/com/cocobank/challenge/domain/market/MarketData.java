package com.cocobank.challenge.domain.market;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record MarketData(
    int id, int instrumentId, BigDecimal close, BigDecimal previousClose, LocalDate date) {

  public MarketData {
    if (id <= 0 || instrumentId <= 0) {
      throw new IllegalArgumentException("Market data identifiers must be positive");
    }
    Objects.requireNonNull(date, "date");
  }

  public boolean hasUsableClose() {
    return close != null && close.signum() > 0;
  }

  public BigDecimal latestClose() {
    return hasUsableClose() ? close : null;
  }

  public boolean hasValidPreviousClose() {
    return previousClose != null && previousClose.signum() > 0;
  }

  public BigDecimal dailyPriceChangeAt(LocalDate day) {
    if (!day.equals(date) || !hasUsableClose() || !hasValidPreviousClose()) {
      return null;
    }
    return close.subtract(previousClose);
  }
}
