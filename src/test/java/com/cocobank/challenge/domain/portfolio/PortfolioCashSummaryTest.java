package com.cocobank.challenge.domain.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import com.cocobank.challenge.domain.order.Order;
import com.cocobank.challenge.domain.order.OrderSide;
import com.cocobank.challenge.domain.order.OrderStatus;
import com.cocobank.challenge.domain.order.OrderType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PortfolioCashSummaryTest {

  @Test
  void usesFilledMovementsAndNewBuyReservations() {
    PortfolioCashSummary cash =
        PortfolioCashSummary.calculate(
            List.of(
                movement(66, 1_000, BigDecimal.ONE, OrderSide.CASH_IN, OrderStatus.FILLED),
                movement(31, 4, BigDecimal.TEN, OrderSide.BUY, OrderStatus.FILLED),
                movement(31, 2, BigDecimal.valueOf(15), OrderSide.SELL, OrderStatus.FILLED),
                movement(31, 3, BigDecimal.valueOf(20), OrderSide.BUY, OrderStatus.NEW),
                movement(31, 100, BigDecimal.valueOf(20), OrderSide.BUY, OrderStatus.CANCELLED)));

    assertThat(cash.available()).isEqualByComparingTo("930");
    assertThat(cash.reserved()).isEqualByComparingTo("60");
  }

  private Order movement(
      int instrumentId, int size, BigDecimal price, OrderSide side, OrderStatus status) {
    return new Order(1, 1, instrumentId, side, OrderType.MARKET, size, price, status, null);
  }
}
