package com.cocobank.challenge.domain.order;

public class OrderNotCancellableException extends RuntimeException {

  public OrderNotCancellableException(OrderStatus status) {
    super("La orden no puede cancelarse porque su estado actual es " + status + ".");
  }
}
