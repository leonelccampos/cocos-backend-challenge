package com.cocobank.challenge.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

  @Test
  void cancelsANewOrderPreservingItsOtherFields() {
    Order pending =
        new Order(
            1,
            1,
            31,
            OrderSide.BUY,
            OrderType.LIMIT,
            2,
            new BigDecimal("10.00"),
            OrderStatus.NEW,
            LocalDateTime.parse("2026-01-02T12:00:00"));

    Order cancelled = pending.cancel();

    assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(cancelled.id()).isEqualTo(pending.id());
    assertThat(cancelled.size()).isEqualTo(pending.size());
    assertThat(cancelled.price()).isEqualByComparingTo(pending.price());
    assertThat(cancelled.reservedCash()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void rejectsCancellingAnOrderThatIsNotNew() {
    Order filled = order(OrderSide.BUY, OrderStatus.FILLED);
    Order cancelled = order(OrderSide.BUY, OrderStatus.CANCELLED);
    Order rejected = order(OrderSide.BUY, OrderStatus.REJECTED);

    assertThatThrownBy(filled::cancel).isInstanceOf(OrderNotCancellableException.class);
    assertThatThrownBy(cancelled::cancel).isInstanceOf(OrderNotCancellableException.class);
    assertThatThrownBy(rejected::cancel).isInstanceOf(OrderNotCancellableException.class);
  }

  private Order order(OrderSide side, OrderStatus status) {
    return new Order(1, 1, 31, side, null, null, null, status, null);
  }
}
