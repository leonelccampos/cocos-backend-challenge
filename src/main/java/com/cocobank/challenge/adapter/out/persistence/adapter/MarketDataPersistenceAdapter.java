package com.cocobank.challenge.adapter.out.persistence.adapter;

import com.cocobank.challenge.adapter.out.persistence.entity.MarketDataEntity;
import com.cocobank.challenge.adapter.out.persistence.repository.MarketDataRepository;
import com.cocobank.challenge.application.port.out.MarketDataPort;
import com.cocobank.challenge.domain.market.MarketData;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Repository;

@Repository
public class MarketDataPersistenceAdapter implements MarketDataPort {

  private final MarketDataRepository repository;

  public MarketDataPersistenceAdapter(MarketDataRepository repository) {
    this.repository = repository;
  }

  @Override
  public Optional<MarketData> lastMarketData(int instrumentId) {
    return repository.findLastMarketData(instrumentId).map(MarketDataEntity::toDomain);
  }

  @Override
  public List<MarketData> findByInstrumentIds(Set<Integer> instrumentIds) {
    if (instrumentIds.isEmpty()) {
      return List.of();
    }
    return repository.findByInstrumentIdInAndDateIsNotNull(instrumentIds).stream()
        .map(MarketDataEntity::toDomain)
        .toList();
  }
}
