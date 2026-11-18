package com.cocobank.challenge.application.dto;

import com.cocobank.challenge.domain.order.Order;
import com.cocobank.challenge.domain.order.OrderRejectionReason;
import com.fasterxml.jackson.annotation.JsonIgnore;

public record OrderSubmissionResult(Order order, OrderRejectionReason rejectionReason) {

  public static OrderSubmissionResult accepted(Order order) {
    return new OrderSubmissionResult(order, null);
  }

  public static OrderSubmissionResult rejected(Order order, OrderRejectionReason reason) {
    if (reason == null) {
      throw new IllegalArgumentException("A rejection requires a reason");
    }
    return new OrderSubmissionResult(order, reason);
  }

  @JsonIgnore
  public boolean isRejected() {
    return rejectionReason != null;
  }
}
