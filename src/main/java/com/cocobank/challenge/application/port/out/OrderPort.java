package com.cocobank.challenge.application.port.out;

import com.cocobank.challenge.application.dto.CreateOrderData;
import com.cocobank.challenge.domain.order.Order;
import java.util.List;

public interface OrderPort {

  List<Order> findByUserId(int userId);

  Order save(CreateOrderData data);
}
