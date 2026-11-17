package com.cocobank.challenge.instrumentsearch.adapter.out.persistence.adapter;

import com.cocobank.challenge.instrumentsearch.adapter.out.persistence.repository.InstrumentSearchProjection;
import com.cocobank.challenge.instrumentsearch.adapter.out.persistence.repository.InstrumentSearchRepository;
import com.cocobank.challenge.instrumentsearch.application.port.out.InstrumentSearchReadPort;
import com.cocobank.challenge.instrumentsearch.domain.InstrumentSearchCandidate;
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
  public List<InstrumentSearchCandidate> findAllInstrumentSearchCandidates() {
    return repository.findAllInstrumentSearchCandidates().stream()
        .map(InstrumentSearchReadAdapter::toCandidate)
        .toList();
  }

  private static InstrumentSearchCandidate toCandidate(InstrumentSearchProjection projection) {
    return new InstrumentSearchCandidate(
        projection.getId(),
        projection.getTicker(),
        projection.getName(),
        projection.getType(),
        projection.getLatestPositiveClose(),
        projection.getLatestPositiveCloseDate(),
        projection.getLatestDatedPreviousClose());
  }
}
