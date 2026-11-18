package com.cocobank.challenge.domain.order;

public class InvalidOrderDataException extends IllegalArgumentException {

  public InvalidOrderDataException(String message) {
    super(message);
  }
}
