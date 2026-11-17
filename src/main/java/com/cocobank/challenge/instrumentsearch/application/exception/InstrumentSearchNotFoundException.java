package com.cocobank.challenge.instrumentsearch.application.exception;

public final class InstrumentSearchNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public InstrumentSearchNotFoundException() {
    super("No matching instruments were found");
  }
}
