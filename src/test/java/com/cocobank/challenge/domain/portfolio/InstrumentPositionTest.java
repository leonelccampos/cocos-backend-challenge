package com.cocobank.challenge.domain.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import com.cocobank.challenge.domain.order.Order;
import com.cocobank.challenge.domain.order.OrderSide;
import com.cocobank.challenge.domain.order.OrderStatus;
import com.cocobank.challenge.domain.order.OrderType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class InstrumentPositionTest {

  @Test
  void preservesHoldingsAndClampsPendingSalesToZero() {
    List<Order> movements =
        List.of(
            movement(31, 5, BigDecimal.TEN, OrderSide.BUY, OrderStatus.FILLED),
            movement(31, 2, BigDecimal.TEN, OrderSide.SELL, OrderStatus.FILLED),
            movement(31, 2, BigDecimal.TEN, OrderSide.SELL, OrderStatus.NEW));

    assertThat(InstrumentPosition.calculate(31, movements)).isEqualTo(new InstrumentPosition(3, 1));
    assertThat(InstrumentPosition.calculate(31, movements.subList(0, 1)))
        .isEqualTo(new InstrumentPosition(5, 5));

    InstrumentPosition shortPosition =
        InstrumentPosition.calculate(
            31,
            List.of(
                movement(31, 2, BigDecimal.TEN, OrderSide.SELL, OrderStatus.FILLED),
                movement(31, 4, BigDecimal.TEN, OrderSide.SELL, OrderStatus.NEW)));
    assertThat(shortPosition).isEqualTo(new InstrumentPosition(-2, 0));
  }

  private Order movement(
      int instrumentId, int size, BigDecimal price, OrderSide side, OrderStatus status) {
    return new Order(1, 1, instrumentId, side, OrderType.MARKET, size, price, status, null);
  }
}
