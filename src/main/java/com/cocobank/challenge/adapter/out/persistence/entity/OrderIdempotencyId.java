package com.cocobank.challenge.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class OrderIdempotencyId implements Serializable {

  @Column(name = "userid")
  private Integer userId;

  @Column(name = "idempotencykey")
  private String idempotencyKey;

  protected OrderIdempotencyId() {}

  public OrderIdempotencyId(int userId, String idempotencyKey) {
    this.userId = userId;
    this.idempotencyKey = idempotencyKey;
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    return object instanceof OrderIdempotencyId other
        && Objects.equals(userId, other.userId)
        && Objects.equals(idempotencyKey, other.idempotencyKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(userId, idempotencyKey);
  }
}
