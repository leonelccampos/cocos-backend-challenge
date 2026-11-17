package com.cocobank.challenge.instrumentsearch.adapter.out.persistence.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface InstrumentSearchProjection {

  Integer getId();

  String getTicker();

  String getName();

  String getType();

  BigDecimal getMarketPrice();

  LocalDate getCloseDate();
}
