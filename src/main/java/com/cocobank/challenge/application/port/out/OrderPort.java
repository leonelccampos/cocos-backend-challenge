package com.cocobank.challenge.application.port.out;

import com.cocobank.challenge.application.dto.CreateOrderData;
import com.cocobank.challenge.domain.order.Order;
import java.util.List;
import java.util.Optional;

public interface OrderPort {

  List<Order> findByUserId(int userId);

  Optional<Order> findById(int orderId);

  Order save(CreateOrderData data);

  Order update(Order order);
}
