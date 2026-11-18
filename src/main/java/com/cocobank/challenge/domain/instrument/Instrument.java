package com.cocobank.challenge.domain.instrument;

import java.util.Objects;

public record Instrument(int id, String ticker, String name, String type) {

  private static final String SHARES_TYPE = "ACCIONES";

  public Instrument {
    if (id <= 0) {
      throw new IllegalArgumentException("id must be positive");
    }
    Objects.requireNonNull(type, "type");
    if (type.isBlank()) {
      throw new IllegalArgumentException("type must not be blank");
    }
  }

  public boolean isTradable() {
    return SHARES_TYPE.equals(type);
  }
}
