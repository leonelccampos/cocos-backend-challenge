package com.cocobank.challenge.instrumentsearch.application.port.out;

import com.cocobank.challenge.instrumentsearch.application.dto.InstrumentSearchField;
import com.cocobank.challenge.instrumentsearch.application.dto.InstrumentSearchResult;
import java.util.List;

public interface InstrumentSearchReadPort {

  List<InstrumentSearchResult> search(String query, InstrumentSearchField field);
}
