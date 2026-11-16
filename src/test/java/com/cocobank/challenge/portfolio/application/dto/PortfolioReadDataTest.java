package com.cocobank.challenge.portfolio.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cocobank.challenge.portfolio.domain.MarketPrice;
import com.cocobank.challenge.portfolio.domain.OrderMovement;
import com.cocobank.challenge.portfolio.domain.OrderSide;
import com.cocobank.challenge.portfolio.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PortfolioReadDataTest {

  @Test
  void copiesMovementsAndPricesIntoImmutableCollections() {
    OrderMovement movement =
        new OrderMovement(
            7,
            3,
            new BigDecimal("10.50"),
            OrderSide.BUY,
            OrderStatus.FILLED,
            "TICK",
            "Instrument",
            "EQUITY",
            null);
    List<OrderMovement> movements = new ArrayList<>(List.of(movement));
    Map<Integer, MarketPrice> prices =
        new HashMap<>(
            Map.of(
                7, new MarketPrice(new BigDecimal("12.25"), LocalDate.parse("2026-11-15"), null)));

    PortfolioReadData data = new PortfolioReadData(movements, prices);

    movements.clear();
    prices.clear();

    assertThat(data.orderMovements()).containsExactly(movement);
    assertThat(data.latestPrices())
        .containsEntry(
            7, new MarketPrice(new BigDecimal("12.25"), LocalDate.parse("2026-11-15"), null));
    assertThatThrownBy(() -> data.orderMovements().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> data.latestPrices().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void preservesMissingMarketPriceValuesInsteadOfInventingAmounts() {
    MarketPrice priceWithoutClose = new MarketPrice(null, null, null);
    PortfolioReadData data = new PortfolioReadData(List.of(), Map.of(7, priceWithoutClose));

    assertThat(data.latestPrices().get(7).close()).isNull();
    assertThat(data.latestPrices().get(7).date()).isNull();
    assertThat(data.latestPrices()).doesNotContainKey(8);
  }
}
