package com.cocobank.challenge.domain.portfolio;

import com.cocobank.challenge.domain.order.Order;
import java.util.List;

public record InstrumentPosition(int quantity, int availableQuantity) {

  public InstrumentPosition {
    if (availableQuantity < 0 || availableQuantity > Math.max(0, quantity)) {
      throw new IllegalArgumentException("availableQuantity is out of range");
    }
  }

  public static InstrumentPosition calculate(int instrumentId, List<Order> orders) {
    int quantity = 0;
    int reserved = 0;

    for (Order order : orders) {
      quantity = Math.addExact(quantity, order.executedQuantityDelta(instrumentId));
      reserved = Math.addExact(reserved, order.reservedQuantity(instrumentId));
    }

    return new InstrumentPosition(quantity, Math.max(0, Math.subtractExact(quantity, reserved)));
  }
}
