package com.cocobank.challenge.adapter.out.persistence.entity;

import com.cocobank.challenge.domain.market.MarketData;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "marketdata")
public class MarketDataEntity {

  @Id private Integer id;

  @Column(name = "instrumentid")
  private Integer instrumentId;

  @Column(name = "close")
  private BigDecimal close;

  @Column(name = "previousclose")
  private BigDecimal previousClose;

  private LocalDate date;

  protected MarketDataEntity() {}

  public MarketData toDomain() {
    return new MarketData(id, instrumentId, close, previousClose, date);
  }
}
