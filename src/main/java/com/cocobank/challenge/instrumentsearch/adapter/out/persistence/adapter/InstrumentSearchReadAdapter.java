package com.cocobank.challenge.instrumentsearch.adapter.out.persistence.adapter;

import com.cocobank.challenge.instrumentsearch.adapter.out.persistence.repository.InstrumentSearchProjection;
import com.cocobank.challenge.instrumentsearch.adapter.out.persistence.repository.InstrumentSearchRepository;
import com.cocobank.challenge.instrumentsearch.application.dto.InstrumentSearchField;
import com.cocobank.challenge.instrumentsearch.application.dto.InstrumentSearchResult;
import com.cocobank.challenge.instrumentsearch.application.port.out.InstrumentSearchReadPort;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class InstrumentSearchReadAdapter implements InstrumentSearchReadPort {

  private final InstrumentSearchRepository repository;

  public InstrumentSearchReadAdapter(InstrumentSearchRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional(readOnly = true)
  public List<InstrumentSearchResult> search(String query, InstrumentSearchField field) {
    return repository.search(query, field.name()).stream()
        .map(InstrumentSearchReadAdapter::toResult)
        .toList();
  }

  private static InstrumentSearchResult toResult(InstrumentSearchProjection projection) {
    return new InstrumentSearchResult(
        projection.getId(),
        projection.getTicker(),
        projection.getName(),
        projection.getType(),
        projection.getMarketPrice(),
        projection.getCloseDate());
  }
}
