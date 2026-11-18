package com.cocobank.challenge.adapter.in.rest.representation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

class InstrumentSearchRequestTest {

  @Test
  void acceptsNonblankQueryAndExactFieldNames() {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      Validator validator = factory.getValidator();

      assertThat(validator.validate(new InstrumentSearchRequest(" abc ", "ticker"))).isEmpty();
      assertThat(validator.validate(new InstrumentSearchRequest("nombre", "name"))).isEmpty();
    }
  }

  @Test
  void rejectsMissingOrBlankQueryAndInvalidField() {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      Validator validator = factory.getValidator();

      assertThat(validator.validate(new InstrumentSearchRequest(null, "ticker"))).isNotEmpty();
      assertThat(validator.validate(new InstrumentSearchRequest("", "ticker"))).isNotEmpty();
      assertThat(validator.validate(new InstrumentSearchRequest(" \t\u2003", "ticker")))
          .isNotEmpty();
      assertThat(validator.validate(new InstrumentSearchRequest("abc", null))).isNotEmpty();
      assertThat(validator.validate(new InstrumentSearchRequest("abc", ""))).isNotEmpty();
      assertThat(validator.validate(new InstrumentSearchRequest("abc", "Ticker"))).isNotEmpty();
      assertThat(validator.validate(new InstrumentSearchRequest("abc", " ticker "))).isNotEmpty();
      assertThat(validator.validate(new InstrumentSearchRequest("abc", "other"))).isNotEmpty();
    }
  }
}
