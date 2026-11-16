package com.cocobank.challenge.portfolio.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class UserEntity {

  @Id
  @Column(name = "id")
  private Integer id;

  @Column(name = "accountnumber")
  private String accountNumber;

  protected UserEntity() {}

  public Integer getId() {
    return id;
  }

  public String getAccountNumber() {
    return accountNumber;
  }
}
