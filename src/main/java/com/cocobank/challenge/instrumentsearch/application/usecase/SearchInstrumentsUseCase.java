package com.cocobank.challenge.instrumentsearch.application.usecase;

import com.cocobank.challenge.instrumentsearch.application.exception.InstrumentSearchNotFoundException;
import com.cocobank.challenge.instrumentsearch.application.port.out.InstrumentSearchReadPort;
import com.cocobank.challenge.instrumentsearch.domain.InstrumentSearchField;
import com.cocobank.challenge.instrumentsearch.domain.InstrumentSearchPolicy;
import com.cocobank.challenge.instrumentsearch.domain.InstrumentSearchResult;
import java.util.List;

public final class SearchInstrumentsUseCase {

  private final InstrumentSearchReadPort readPort;

  public SearchInstrumentsUseCase(InstrumentSearchReadPort readPort) {
    this.readPort = readPort;
  }

  public List<InstrumentSearchResult> execute(String query, InstrumentSearchField field) {
    List<InstrumentSearchResult> results =
        InstrumentSearchPolicy.search(readPort.findAllInstrumentSearchCandidates(), query, field);
    if (results.isEmpty()) {
      throw new InstrumentSearchNotFoundException();
    }
    return results;
  }
}
