package com.cocobank.challenge.application.port.out;

import com.cocobank.challenge.application.dto.InstrumentSearchField;
import com.cocobank.challenge.application.dto.InstrumentSearchResult;
import java.util.List;

public interface InstrumentSearchReadPort {

  List<InstrumentSearchResult> search(String query, InstrumentSearchField field);
}
