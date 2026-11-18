package com.cocobank.challenge.adapter.out.persistence.repository;

import com.cocobank.challenge.adapter.out.persistence.entity.OrderEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<OrderEntity, Integer> {

  List<OrderEntity> findByUserIdOrderById(int userId);
}
