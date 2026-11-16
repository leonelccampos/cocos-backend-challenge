package com.cocobank.challenge.portfolio.application.exception;

public final class PortfolioNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public PortfolioNotFoundException() {
    super("The requested portfolio was not found");
  }
}
