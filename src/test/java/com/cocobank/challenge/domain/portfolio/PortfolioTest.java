package com.cocobank.challenge.domain.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PortfolioTest {

  @Test
  void copiesInstrumentListAndKeepsAmounts() {
    PortfolioInstrument instrument = availableInstrument();
    List<PortfolioInstrument> instruments = new ArrayList<>(List.of(instrument));

    Portfolio portfolio =
        new Portfolio(
            new BigDecimal("1000.00"),
            new BigDecimal("200.00"),
            new BigDecimal("1500.00"),
            instruments);

    instruments.clear();

    assertThat(portfolio.availableCash()).isEqualByComparingTo("1000.00");
    assertThat(portfolio.reservedCash()).isEqualByComparingTo("200.00");
    assertThat(portfolio.totalAccountValue()).isEqualByComparingTo("1500.00");
    assertThat(portfolio.instruments()).containsExactly(instrument);
    assertThatThrownBy(() -> portfolio.instruments().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private PortfolioInstrument availableInstrument() {
    return new PortfolioInstrument(
        7,
        "TICK",
        "Instrument",
        "EQUITY",
        1,
        1,
        new BigDecimal("10.50"),
        new BigDecimal("2.50"),
        null,
        null,
        ValuationStatus.AVAILABLE);
  }
}
