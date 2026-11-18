package com.cocobank.challenge.domain.market;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketDataSelectionTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 1, 2);

  @Test
  void selectsMostRecentPositiveCloseByDateAndIdBeforePreviousClose() {
    MarketDataSelection selection =
        MarketDataSelection.select(
                List.of(
                    data(1, TODAY.minusDays(1), "10", null),
                    data(2, TODAY.minusDays(1), "20", null),
                    data(3, TODAY, "0", "999")))
            .orElseThrow();

    assertThat(selection.close()).isEqualByComparingTo("20");
    assertThat(selection.selectedRecord().id()).isEqualTo(2);
    assertThat(selection.usesHistoricalClose()).isTrue();
    assertThat(selection.dailyPriceChangeAt(TODAY)).isNull();
  }

  @Test
  void usesOnlyLatestDatedRecordsPreviousCloseWhenThereIsNoPositiveClose() {
    MarketDataSelection selection =
        MarketDataSelection.select(
                List.of(
                    data(1, TODAY.minusDays(1), "0", "10"),
                    data(2, TODAY, "-1", "20"),
                    data(3, TODAY, null, "30")))
            .orElseThrow();

    assertThat(selection.close()).isEqualByComparingTo("30");
    assertThat(selection.usesPreviousClose()).isTrue();
    assertThat(selection.dailyPriceChangeAt(TODAY)).isNull();

    MarketDataSelection unavailable =
        MarketDataSelection.select(
                List.of(data(1, TODAY.minusDays(1), "0", "10"), data(2, TODAY, "-1", "0")))
            .orElseThrow();
    assertThat(unavailable.close()).isNull();
  }

  @Test
  void dailyReturnUsesPreviousCloseOnlyOnLatestCurrentRecord() {
    MarketDataSelection selection =
        MarketDataSelection.select(List.of(data(1, TODAY, "10", "9"), data(2, TODAY, "20", "15")))
            .orElseThrow();
    assertThat(selection.close()).isEqualByComparingTo("20");
    assertThat(selection.dailyPriceChangeAt(TODAY)).isEqualByComparingTo("5");
    assertThat(selection.usesHistoricalClose()).isFalse();
    assertThat(MarketDataSelection.select(List.of())).isEmpty();
  }

  private MarketData data(int id, LocalDate date, String close, String previousClose) {
    return new MarketData(id, 31, decimal(close), decimal(previousClose), date);
  }

  private BigDecimal decimal(String value) {
    return value == null ? null : new BigDecimal(value);
  }
}
