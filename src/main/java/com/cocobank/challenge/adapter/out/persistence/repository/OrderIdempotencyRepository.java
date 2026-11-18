package com.cocobank.challenge.adapter.out.persistence.repository;

import com.cocobank.challenge.adapter.out.persistence.entity.OrderIdempotencyEntity;
import com.cocobank.challenge.adapter.out.persistence.entity.OrderIdempotencyId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderIdempotencyRepository
    extends JpaRepository<OrderIdempotencyEntity, OrderIdempotencyId> {}
