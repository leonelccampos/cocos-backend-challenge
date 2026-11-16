package com.cocobank.challenge.portfolio.adapter.out.persistence.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface OrderMovementProjection {
  Integer getInstrumentId();

  Integer getSize();

  BigDecimal getPrice();

  String getSide();

  String getStatus();

  String getTicker();

  String getName();

  String getInstrumentType();

  LocalDateTime getDateTime();
}
