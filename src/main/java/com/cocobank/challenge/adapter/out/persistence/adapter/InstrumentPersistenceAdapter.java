package com.cocobank.challenge.adapter.out.persistence.adapter;

import com.cocobank.challenge.adapter.out.persistence.entity.InstrumentEntity;
import com.cocobank.challenge.adapter.out.persistence.repository.InstrumentRepository;
import com.cocobank.challenge.application.port.out.InstrumentPort;
import com.cocobank.challenge.domain.instrument.Instrument;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Repository;

@Repository
public class InstrumentPersistenceAdapter implements InstrumentPort {

  private final InstrumentRepository repository;

  public InstrumentPersistenceAdapter(InstrumentRepository repository) {
    this.repository = repository;
  }

  @Override
  public Optional<Instrument> find(int instrumentId) {
    return repository.findById(instrumentId).map(InstrumentEntity::toDomain);
  }

  @Override
  public List<Instrument> findByIds(Set<Integer> instrumentIds) {
    return repository.findAllById(instrumentIds).stream().map(InstrumentEntity::toDomain).toList();
  }
}
