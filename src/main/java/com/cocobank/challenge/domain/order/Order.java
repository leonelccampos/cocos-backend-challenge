package com.cocobank.challenge.domain.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

public record Order(
    int id,
    int userId,
    int instrumentId,
    OrderSide side,
    OrderType type,
    Integer size,
    BigDecimal price,
    OrderStatus status,
    LocalDateTime datetime) {

  public Order {
    if (id <= 0 || userId <= 0 || instrumentId <= 0) {
      throw new IllegalArgumentException("Order identifiers must be positive");
    }
    Objects.requireNonNull(side, "side");
    Objects.requireNonNull(status, "status");
  }

  public boolean affectsExecutedPortfolio() {
    return status == OrderStatus.FILLED && (side == OrderSide.BUY || side == OrderSide.SELL);
  }

  public BigDecimal executedCashDelta() {
    if (status != OrderStatus.FILLED) {
      return BigDecimal.ZERO;
    }
    return switch (side) {
      case CASH_IN -> BigDecimal.valueOf(requiredSize());
      case CASH_OUT -> BigDecimal.valueOf(requiredSize()).negate();
      case BUY -> value().negate();
      case SELL -> value();
    };
  }

  public BigDecimal reservedCash() {
    return status == OrderStatus.NEW && side == OrderSide.BUY ? value() : BigDecimal.ZERO;
  }

  public int executedQuantityDelta(int targetInstrumentId) {
    if (instrumentId != targetInstrumentId || status != OrderStatus.FILLED) {
      return 0;
    }
    return switch (side) {
      case BUY -> requiredSize();
      case SELL -> Math.negateExact(requiredSize());
      case CASH_IN, CASH_OUT -> 0;
    };
  }

  public int reservedQuantity(int targetInstrumentId) {
    return instrumentId == targetInstrumentId && status == OrderStatus.NEW && side == OrderSide.SELL
        ? requiredSize()
        : 0;
  }

  public BigDecimal value() {
    if (price == null || size == null) {
      throw new InvalidOrderDataException("A price and size are required for an order value");
    }
    return price.multiply(BigDecimal.valueOf(size));
  }

  private int requiredSize() {
    if (size == null) {
      throw new InvalidOrderDataException("A size is required for this order");
    }
    return size;
  }
}
