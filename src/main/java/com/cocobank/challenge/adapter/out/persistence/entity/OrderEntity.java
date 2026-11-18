package com.cocobank.challenge.adapter.out.persistence.entity;

import com.cocobank.challenge.application.dto.CreateOrderData;
import com.cocobank.challenge.domain.order.InvalidOrderDataException;
import com.cocobank.challenge.domain.order.Order;
import com.cocobank.challenge.domain.order.OrderSide;
import com.cocobank.challenge.domain.order.OrderStatus;
import com.cocobank.challenge.domain.order.OrderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
public class OrderEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(name = "instrumentid")
  private Integer instrumentId;

  @Column(name = "userid")
  private Integer userId;

  private Integer size;
  private BigDecimal price;
  private String type;
  private String side;
  private String status;
  private LocalDateTime datetime;

  protected OrderEntity() {}

  private OrderEntity(CreateOrderData data) {
    this.instrumentId = data.instrumentId();
    this.userId = data.userId();
    this.size = data.size();
    this.price = data.price();
    this.type = data.type().name();
    this.side = data.side().name();
    this.status = data.status().name();
    this.datetime = data.datetime();
  }

  public static OrderEntity from(CreateOrderData data) {
    return new OrderEntity(data);
  }

  public Order toDomain() {
    if (id == null || userId == null || instrumentId == null) {
      throw new InvalidOrderDataException("Order is missing an identifier");
    }
    if (side == null || status == null) {
      throw new InvalidOrderDataException("Order is missing its side or status");
    }
    OrderSide orderSide;
    OrderStatus orderStatus;
    OrderType orderType;
    try {
      orderSide = OrderSide.valueOf(side);
      orderStatus = OrderStatus.valueOf(status);
      orderType = type == null ? null : OrderType.valueOf(type);
    } catch (IllegalArgumentException exception) {
      throw new InvalidOrderDataException("Order has an unsupported side, status or type");
    }
    return new Order(
        id, userId, instrumentId, orderSide, orderType, size, price, orderStatus, datetime);
  }
}
