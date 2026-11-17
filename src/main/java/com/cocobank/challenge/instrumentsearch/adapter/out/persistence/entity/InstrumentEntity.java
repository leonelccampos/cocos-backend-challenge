package com.cocobank.challenge.instrumentsearch.adapter.out.persistence.entity;

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

  protected InstrumentEntity() {}
}
