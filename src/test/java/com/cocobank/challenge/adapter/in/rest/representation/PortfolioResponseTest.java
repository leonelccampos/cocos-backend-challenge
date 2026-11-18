package com.cocobank.challenge.adapter.in.rest.representation;

import static org.assertj.core.api.Assertions.assertThat;

import com.cocobank.challenge.domain.portfolio.Portfolio;
import com.cocobank.challenge.domain.portfolio.PortfolioInstrument;
import com.cocobank.challenge.domain.portfolio.ValuationStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PortfolioResponseTest {

  @Test
  void mapsPortfolioWithoutExposingReservedCashAndPreservesUnavailableValues() throws Exception {
    Portfolio portfolio =
        new Portfolio(
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(25),
            BigDecimal.valueOf(200),
            List.of(
                new PortfolioInstrument(
                    31,
                    "TICK",
                    "Instrument",
                    "EQUITY",
                    2,
                    1,
                    BigDecimal.valueOf(50),
                    BigDecimal.TEN,
                    BigDecimal.valueOf(25),
                    BigDecimal.valueOf(4),
                    ValuationStatus.AVAILABLE),
                new PortfolioInstrument(
                    32,
                    "MISS",
                    "Missing price",
                    "EQUITY",
                    1,
                    0,
                    null,
                    null,
                    null,
                    null,
                    ValuationStatus.UNAVAILABLE)));

    PortfolioResponse response = PortfolioResponse.from(portfolio);
    JsonNode json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(response));

    assertThat(json.get("availableCash").decimalValue()).isEqualByComparingTo("100");
    assertThat(json.get("totalAccountValue").decimalValue()).isEqualByComparingTo("200");
    assertThat(json.get("reservedCash")).isNull();
    assertThat(json.at("/instruments/0/marketValue").decimalValue()).isEqualByComparingTo("50");
    assertThat(json.at("/instruments/0/availableQuantity").asInt()).isEqualTo(1);
    assertThat(json.at("/instruments/0/performance").decimalValue()).isEqualByComparingTo("25");
    assertThat(json.at("/instruments/0/dailyReturn").decimalValue()).isEqualByComparingTo("4");
    assertThat(json.at("/instruments/0").has("accumulatedReturnPercentage")).isFalse();
    assertThat(json.at("/instruments/1/marketValue").isNull()).isTrue();
    assertThat(json.at("/instruments/1/availableQuantity").asInt()).isZero();
    assertThat(json.at("/instruments/1/accumulatedReturn").isNull()).isTrue();
    assertThat(json.at("/instruments/1").has("performance")).isTrue();
    assertThat(json.at("/instruments/1/performance").isNull()).isTrue();
    assertThat(json.at("/instruments/1/dailyReturn").isNull()).isTrue();
    assertThat(json.at("/instruments/1").has("accumulatedReturnPercentage")).isFalse();
    assertThat(json.at("/instruments/1/valuationStatus").asText()).isEqualTo("UNAVAILABLE");
  }
}
