package com.cocobank.challenge.domain.user;

import java.util.Objects;

public record User(int id, String accountNumber) {

  public User {
    if (id <= 0) {
      throw new IllegalArgumentException("id must be positive");
    }
  }

  public boolean hasAccountNumber(String accountNumber) {
    return accountNumber != null && Objects.equals(this.accountNumber, accountNumber);
  }
}
