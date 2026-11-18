package com.cocobank.challenge.domain.portfolio;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public record Portfolio(
    BigDecimal availableCash,
    BigDecimal reservedCash,
    BigDecimal totalAccountValue,
    List<PortfolioInstrument> instruments) {

  public Portfolio {
    Objects.requireNonNull(availableCash, "availableCash");
    Objects.requireNonNull(reservedCash, "reservedCash");
    Objects.requireNonNull(totalAccountValue, "totalAccountValue");
    instruments = List.copyOf(instruments);
  }
}
