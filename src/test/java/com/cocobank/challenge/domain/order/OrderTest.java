package com.cocobank.challenge.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OrderTest {

  @Test
  void rejectsMissingSizeWhenItIsNeededForCashOrQuantity() {
    Order cash = order(OrderSide.CASH_IN, OrderStatus.FILLED);
    Order executedSale = order(OrderSide.SELL, OrderStatus.FILLED);
    Order pendingSale = order(OrderSide.SELL, OrderStatus.NEW);

    assertThatThrownBy(cash::executedCashDelta).isInstanceOf(InvalidOrderDataException.class);
    assertThatThrownBy(() -> executedSale.executedQuantityDelta(31))
        .isInstanceOf(InvalidOrderDataException.class);
    assertThatThrownBy(() -> pendingSale.reservedQuantity(31))
        .isInstanceOf(InvalidOrderDataException.class);
  }

  @Test
  void ignoresMissingSizeForRejectedOrders() {
    Order rejected = order(OrderSide.SELL, OrderStatus.REJECTED);
    assertThat(rejected.executedCashDelta()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(rejected.executedQuantityDelta(31)).isZero();
    assertThat(rejected.reservedQuantity(31)).isZero();
  }

  private Order order(OrderSide side, OrderStatus status) {
    return new Order(1, 1, 31, side, null, null, null, status, null);
  }
}
