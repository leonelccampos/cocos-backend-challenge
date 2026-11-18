package com.cocobank.challenge.adapter.out.persistence.entity;

import com.cocobank.challenge.domain.instrument.Instrument;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "instruments")
public class InstrumentEntity {

  @Id
  @Column(name = "id")
  private Integer id;

  @Column(name = "ticker")
  private String ticker;

  @Column(name = "name")
  private String name;

  @Column(name = "type")
  private String type;

  protected InstrumentEntity() {}

  public String type() {
    return type;
  }

  public Instrument toDomain() {
    return new Instrument(id, ticker, name, type);
  }
}
