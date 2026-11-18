package com.cocobank.challenge.domain.instrument;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InstrumentTest {

  @Test
  void onlySharesAreTradable() {
    assertThat(new Instrument(1, "TEST", "Test", "ACCIONES").isTradable()).isTrue();
    assertThat(new Instrument(66, "ARS", "Peso", "MONEDA").isTradable()).isFalse();
  }
}
