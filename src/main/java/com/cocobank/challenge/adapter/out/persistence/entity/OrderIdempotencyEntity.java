package com.cocobank.challenge.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "order_idempotency")
public class OrderIdempotencyEntity {

  @EmbeddedId private OrderIdempotencyId id;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "requestfingerprint", columnDefinition = "char(64)", length = 64)
  private String requestFingerprint;

  @Column(name = "responsestatus")
  private Short responseStatus;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "responsebody")
  private String responseBody;

  @Column(name = "orderid")
  private Integer orderId;

  protected OrderIdempotencyEntity() {}

  public OrderIdempotencyEntity(
      OrderIdempotencyId id,
      String requestFingerprint,
      int responseStatus,
      String responseBody,
      int orderId) {
    this.id = id;
    this.requestFingerprint = requestFingerprint;
    this.responseStatus = (short) responseStatus;
    this.responseBody = responseBody;
    this.orderId = orderId;
  }

  public String getRequestFingerprint() {
    return requestFingerprint;
  }

  public int getResponseStatus() {
    return responseStatus;
  }

  public String getResponseBody() {
    return responseBody;
  }
}
