package com.cocobank.challenge.application.exception;

public class IdempotencyConflictException extends RuntimeException {

  public IdempotencyConflictException() {
    super("Idempotency-Key ya fue utilizada con datos distintos.");
  }
}
