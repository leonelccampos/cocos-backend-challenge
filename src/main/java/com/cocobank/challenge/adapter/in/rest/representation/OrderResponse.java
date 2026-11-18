package com.cocobank.challenge.adapter.in.rest.representation;

import com.cocobank.challenge.domain.order.Order;
import com.cocobank.challenge.domain.order.OrderSide;
import com.cocobank.challenge.domain.order.OrderStatus;
import com.cocobank.challenge.domain.order.OrderType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderResponse(
    int id,
    int userId,
    int instrumentId,
    OrderSide side,
    OrderType type,
    Integer size,
    BigDecimal price,
    OrderStatus status,
    LocalDateTime datetime) {

  public static OrderResponse from(Order order) {
    return new OrderResponse(
        order.id(),
        order.userId(),
        order.instrumentId(),
        order.side(),
        order.type(),
        order.size(),
        order.price(),
        order.status(),
        order.datetime());
  }
}
