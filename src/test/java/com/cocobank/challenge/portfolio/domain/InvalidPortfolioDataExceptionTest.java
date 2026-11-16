package com.cocobank.challenge.portfolio.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InvalidPortfolioDataExceptionTest {

  @Test
  void keepsTheSuppliedMessage() {
    InvalidPortfolioDataException exception =
        new InvalidPortfolioDataException("A required order price is missing");

    assertThat(exception).hasMessage("A required order price is missing");
  }
}
