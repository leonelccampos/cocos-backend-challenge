package com.cocobank.challenge.application.port.out;

import com.cocobank.challenge.domain.instrument.Instrument;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface InstrumentPort {

  Optional<Instrument> find(int instrumentId);

  List<Instrument> findByIds(Set<Integer> instrumentIds);
}
