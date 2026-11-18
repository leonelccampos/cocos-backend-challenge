package com.cocobank.challenge.domain.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PortfolioInstrumentTest {

  @Test
  void availableValuationMayHaveUnavailableReturnPercentage() {
    PortfolioInstrument instrument = availableInstrument();

    assertThat(instrument.valuationStatus()).isEqualTo(ValuationStatus.AVAILABLE);
    assertThat(instrument.marketValue()).isEqualByComparingTo("10.50");
    assertThat(instrument.accumulatedReturn()).isEqualByComparingTo("2.50");
    assertThat(instrument.accumulatedReturnPercentage()).isNull();
  }

  @Test
  void unavailableValuationIsExplicitAndDoesNotUseZeroAmounts() {
    PortfolioInstrument instrument =
        new PortfolioInstrument(
            7,
            "TICK",
            "Instrument",
            "EQUITY",
            2,
            2,
            null,
            null,
            null,
            null,
            ValuationStatus.UNAVAILABLE);

    assertThat(instrument.valuationStatus()).isEqualTo(ValuationStatus.UNAVAILABLE);
    assertThat(instrument.marketValue()).isNull();
    assertThat(instrument.accumulatedReturn()).isNull();
    assertThat(instrument.accumulatedReturnPercentage()).isNull();
  }

  @Test
  void rejectsZeroQuantity() {
    assertThatIllegalArgumentException()
        .isThrownBy(() ->
            new PortfolioInstrument(
                7,
                "TICK",
                "Instrument",
                "EQUITY",
                0,
                0,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                ValuationStatus.AVAILABLE));
  }

  @Test
  void rejectsMarketValueWithOppositeSignToQuantity() {
    assertThatIllegalArgumentException()
        .isThrownBy(() ->
            new PortfolioInstrument(
                7,
                "TICK",
                "Instrument",
                "EQUITY",
                -1,
                0,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                null,
                null,
                ValuationStatus.AVAILABLE));
  }

  @Test
  void availableValuationRequiresMarketValueAndAccumulatedReturn() {
    assertThatNullPointerException()
        .isThrownBy(() ->
            new PortfolioInstrument(
                7,
                "TICK",
                "Instrument",
                "EQUITY",
                1,
                1,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                ValuationStatus.AVAILABLE));
  }

  @Test
  void unavailableValuationRejectsAmountsInsteadOfTreatingThemAsZero() {
    assertThatIllegalArgumentException()
        .isThrownBy(() ->
            new PortfolioInstrument(
                7,
                "TICK",
                "Instrument",
                "EQUITY",
                1,
                1,
                BigDecimal.ZERO,
                null,
                null,
                null,
                ValuationStatus.UNAVAILABLE));
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
