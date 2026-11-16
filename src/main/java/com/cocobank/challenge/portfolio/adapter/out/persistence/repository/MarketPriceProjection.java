package com.cocobank.challenge.portfolio.adapter.out.persistence.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface MarketPriceProjection {
  Integer getInstrumentId();

  BigDecimal getClose();

  LocalDate getDate();

  BigDecimal getPreviousClose();

  String getFallbackSource();
}
