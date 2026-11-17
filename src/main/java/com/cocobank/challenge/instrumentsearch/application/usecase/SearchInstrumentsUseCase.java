package com.cocobank.challenge.instrumentsearch.application.usecase;

import com.cocobank.challenge.instrumentsearch.application.dto.InstrumentSearchField;
import com.cocobank.challenge.instrumentsearch.application.dto.InstrumentSearchResult;
import com.cocobank.challenge.instrumentsearch.application.exception.InstrumentSearchNotFoundException;
import com.cocobank.challenge.instrumentsearch.application.port.out.InstrumentSearchReadPort;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public final class SearchInstrumentsUseCase {

  private final InstrumentSearchReadPort readPort;

  public SearchInstrumentsUseCase(InstrumentSearchReadPort readPort) {
    this.readPort = readPort;
  }

  public List<InstrumentSearchResult> execute(String query, InstrumentSearchField field) {
    List<InstrumentSearchResult> results = readPort.search(query.strip(), field);
    if (results.isEmpty()) {
      throw new InstrumentSearchNotFoundException();
    }
    return results;
  }
}
