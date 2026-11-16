package com.cocobank.challenge.portfolio.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

public record OrderMovement(
    int instrumentId,
    int size,
    BigDecimal price,
    OrderSide side,
    OrderStatus status,
    String ticker,
    String name,
    String instrumentType,
    LocalDateTime dateTime) {

  public OrderMovement {
    Objects.requireNonNull(side, "side");
    Objects.requireNonNull(status, "status");
  }
}
