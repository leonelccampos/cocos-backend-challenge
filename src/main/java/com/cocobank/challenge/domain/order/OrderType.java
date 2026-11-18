package com.cocobank.challenge.domain.order;

public enum OrderType {
  MARKET(OrderStatus.FILLED),
  LIMIT(OrderStatus.NEW);

  private final OrderStatus acceptedStatus;

  OrderType(OrderStatus acceptedStatus) {
    this.acceptedStatus = acceptedStatus;
  }

  public OrderStatus acceptedStatus() {
    return acceptedStatus;
  }
}
