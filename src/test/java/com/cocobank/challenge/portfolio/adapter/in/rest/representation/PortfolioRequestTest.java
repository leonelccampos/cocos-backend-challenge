package com.cocobank.challenge.portfolio.adapter.in.rest.representation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

class PortfolioRequestTest {

  @Test
  void acceptsNonblankAccountNumberWithinSchemaLimit() {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      Validator validator = factory.getValidator();

      assertThat(validator.validate(new PortfolioRequest("ACCT-001"))).isEmpty();
      assertThat(validator.validate(new PortfolioRequest("A".repeat(20)))).isEmpty();
    }
  }

  @Test
  void rejectsMissingBlankAndOverlongAccountNumbers() {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      Validator validator = factory.getValidator();

      assertThat(validator.validate(new PortfolioRequest(null))).isNotEmpty();
      assertThat(validator.validate(new PortfolioRequest("  "))).isNotEmpty();
      assertThat(validator.validate(new PortfolioRequest("A".repeat(21)))).isNotEmpty();
    }
  }
}
