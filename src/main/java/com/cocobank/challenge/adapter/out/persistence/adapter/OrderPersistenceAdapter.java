package com.cocobank.challenge.adapter.out.persistence.adapter;

import com.cocobank.challenge.adapter.out.persistence.entity.OrderEntity;
import com.cocobank.challenge.adapter.out.persistence.repository.OrderRepository;
import com.cocobank.challenge.application.dto.CreateOrderData;
import com.cocobank.challenge.application.port.out.OrderPort;
import com.cocobank.challenge.domain.order.Order;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class OrderPersistenceAdapter implements OrderPort {

  private final OrderRepository repository;

  public OrderPersistenceAdapter(OrderRepository repository) {
    this.repository = repository;
  }

  @Override
  public List<Order> findByUserId(int userId) {
    return repository.findByUserIdOrderById(userId).stream().map(OrderEntity::toDomain).toList();
  }

  @Override
  public Order save(CreateOrderData data) {
    return repository.save(OrderEntity.from(data)).toDomain();
  }
}
