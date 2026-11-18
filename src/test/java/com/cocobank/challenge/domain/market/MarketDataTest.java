package com.cocobank.challenge.domain.market;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MarketDataTest {

  @Test
  void exposesOnlyUsableCloseAndDailyChangeForTheMarketDate() {
    LocalDate date = LocalDate.of(2026, 1, 2);
    MarketData marketData = new MarketData(1, 31, new BigDecimal("12.50"), BigDecimal.TEN, date);

    assertThat(marketData.latestClose()).isEqualByComparingTo("12.50");
    assertThat(marketData.dailyPriceChangeAt(date)).isEqualByComparingTo("2.50");
    assertThat(marketData.dailyPriceChangeAt(date.minusDays(1))).isNull();
  }

  @Test
  void invalidCloseAndPreviousCloseDoNotProduceExecutionOrDailyValues() {
    LocalDate date = LocalDate.of(2026, 1, 2);

    assertThat(new MarketData(1, 31, BigDecimal.ZERO, BigDecimal.TEN, date).latestClose()).isNull();
    assertThat(
            new MarketData(1, 31, BigDecimal.TEN, BigDecimal.ZERO, date).dailyPriceChangeAt(date))
        .isNull();
  }
}
