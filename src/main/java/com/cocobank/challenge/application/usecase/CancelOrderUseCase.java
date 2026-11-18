package com.cocobank.challenge.application.usecase;

import com.cocobank.challenge.application.dto.CancelOrderCommand;
import com.cocobank.challenge.application.exception.OrderBadRequestException;
import com.cocobank.challenge.application.exception.OrderNotFoundException;
import com.cocobank.challenge.application.port.out.OrderPort;
import com.cocobank.challenge.application.port.out.UserPort;
import com.cocobank.challenge.domain.order.Order;
import com.cocobank.challenge.domain.order.OrderStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CancelOrderUseCase {

  private final UserPort userPort;
  private final OrderPort orderPort;

  public CancelOrderUseCase(UserPort userPort, OrderPort orderPort) {
    this.userPort = userPort;
    this.orderPort = orderPort;
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Order execute(CancelOrderCommand command) {
    validateInput(command);
    if (!userPort.lock(command.userId())) {
      throw new OrderNotFoundException("No se encontró el usuario solicitado.");
    }
    Order order =
        orderPort
            .findById(command.orderId())
            .filter(found -> found.userId() == command.userId())
            .orElseThrow(() -> new OrderNotFoundException("No se encontró la orden solicitada."));
    return orderPort.update(order.cancel());
  }

  private void validateInput(CancelOrderCommand command) {
    if (!OrderStatus.CANCELLED.name().equals(command.status())) {
      throw new OrderBadRequestException("status debe ser CANCELLED.");
    }
  }
}
