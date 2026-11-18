package com.cocobank.challenge.application.exception;

public class OrderNotFoundException extends RuntimeException {

  public OrderNotFoundException(String message) {
    super(message);
  }
}
