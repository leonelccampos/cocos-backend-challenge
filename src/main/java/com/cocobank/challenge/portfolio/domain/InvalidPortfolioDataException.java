package com.cocobank.challenge.portfolio.domain;

public final class InvalidPortfolioDataException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public InvalidPortfolioDataException(String message) {
    super(message);
  }
}
