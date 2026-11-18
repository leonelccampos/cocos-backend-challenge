package com.cocobank.challenge.application.port.out;

import com.cocobank.challenge.domain.market.MarketData;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface MarketDataPort {

  Optional<MarketData> lastMarketData(int instrumentId);

  List<MarketData> findByInstrumentIds(Set<Integer> instrumentIds);
}
