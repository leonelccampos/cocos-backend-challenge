package com.cocobank.challenge.portfolio.application.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PortfolioNotFoundExceptionTest {

  @Test
  void describesTheMissingPortfolioWithoutAccountDetails() {
    PortfolioNotFoundException exception = new PortfolioNotFoundException();

    assertThat(exception).hasMessage("The requested portfolio was not found");
  }
}
