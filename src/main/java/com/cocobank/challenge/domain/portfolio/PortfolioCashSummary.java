package com.cocobank.challenge.domain.portfolio;

import com.cocobank.challenge.domain.order.Order;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public record PortfolioCashSummary(BigDecimal available, BigDecimal reserved) {

  public PortfolioCashSummary {
    Objects.requireNonNull(available, "available");
    Objects.requireNonNull(reserved, "reserved");
    if (reserved.signum() < 0) {
      throw new IllegalArgumentException("reserved must not be negative");
    }
  }

  public static PortfolioCashSummary calculate(List<Order> orders) {
    BigDecimal available = BigDecimal.ZERO;
    BigDecimal reserved = BigDecimal.ZERO;

    for (Order order : orders) {
      BigDecimal reservation = order.reservedCash();
      available = available.add(order.executedCashDelta()).subtract(reservation);
      reserved = reserved.add(reservation);
    }

    return new PortfolioCashSummary(available, reserved);
  }
}
