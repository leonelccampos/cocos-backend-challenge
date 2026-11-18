package com.cocobank.challenge.application.dto;

import com.cocobank.challenge.domain.order.OrderSide;
import com.cocobank.challenge.domain.order.OrderStatus;
import com.cocobank.challenge.domain.order.OrderType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

public record CreateOrderData(
    int userId,
    int instrumentId,
    OrderSide side,
    OrderType type,
    Integer size,
    BigDecimal price,
    OrderStatus status,
    LocalDateTime datetime) {

  public CreateOrderData {
    if (userId <= 0 || instrumentId <= 0) {
      throw new IllegalArgumentException("Order associations must be positive");
    }
    Objects.requireNonNull(side, "side");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(datetime, "datetime");
    if (side != OrderSide.BUY && side != OrderSide.SELL) {
      throw new IllegalArgumentException("Created order side must be BUY or SELL");
    }
    if (status == OrderStatus.NEW || status == OrderStatus.FILLED) {
      if (size == null || size <= 0 || price == null || price.signum() <= 0) {
        throw new IllegalArgumentException("Accepted orders require a positive size and price");
      }
    }
  }
}
