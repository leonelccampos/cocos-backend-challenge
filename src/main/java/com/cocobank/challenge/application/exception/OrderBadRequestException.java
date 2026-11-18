package com.cocobank.challenge.application.exception;

public class OrderBadRequestException extends RuntimeException {

  public OrderBadRequestException(String message) {
    super(message);
  }
}
