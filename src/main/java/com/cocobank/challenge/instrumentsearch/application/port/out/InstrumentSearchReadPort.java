package com.cocobank.challenge.instrumentsearch.application.port.out;

import com.cocobank.challenge.instrumentsearch.domain.InstrumentSearchCandidate;
import java.util.List;

public interface InstrumentSearchReadPort {

  List<InstrumentSearchCandidate> findAllInstrumentSearchCandidates();
}
