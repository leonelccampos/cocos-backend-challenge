package com.cocobank.challenge.instrumentsearch.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class InstrumentSearchResultTest {

  @Test
  void retainsAClosingPriceAndItsDate() {
    InstrumentSearchResult result =
        new InstrumentSearchResult(
            7,
            "TICK",
            "Instrument",
            "ACCIONES",
            new BigDecimal("12.34"),
            LocalDate.of(2023, 7, 14));

    assertThat(result.marketPrice()).isEqualByComparingTo("12.34");
    assertThat(result.closeDate()).isEqualTo(LocalDate.of(2023, 7, 14));
  }

  @Test
  void retainsFallbackPriceWithoutInventingADate() {
    InstrumentSearchResult result =
        new InstrumentSearchResult(
            7, "TICK", "Instrument", "ACCIONES", new BigDecimal("10.50"), null);

    assertThat(result.marketPrice()).isEqualByComparingTo("10.50");
    assertThat(result.closeDate()).isNull();
  }

  @Test
  void retainsUnavailablePriceAndDateAsNull() {
    InstrumentSearchResult result =
        new InstrumentSearchResult(7, null, null, "ACCIONES", null, null);

    assertThat(result.marketPrice()).isNull();
    assertThat(result.closeDate()).isNull();
  }
}
