package com.cocobank.challenge.domain.market;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public record MarketDataSelection(MarketData latest, MarketData latestWithClose) {

  private static final Comparator<MarketData> BY_DATE_AND_ID =
      Comparator.comparing(MarketData::date).thenComparingInt(MarketData::id);

  public static Optional<MarketDataSelection> select(List<MarketData> records) {
    Optional<MarketData> latest = records.stream().max(BY_DATE_AND_ID);
    if (latest.isEmpty()) {
      return Optional.empty();
    }
    MarketData latestWithClose =
        records.stream().filter(MarketData::hasUsableClose).max(BY_DATE_AND_ID).orElse(null);
    return Optional.of(new MarketDataSelection(latest.get(), latestWithClose));
  }

  public static Map<Integer, MarketDataSelection> selectByInstrumentId(
      List<MarketData> marketData) {
    Map<Integer, List<MarketData>> recordsByInstrumentId =
        marketData.stream().collect(Collectors.groupingBy(MarketData::instrumentId));
    Map<Integer, MarketDataSelection> selectionsByInstrumentId = new HashMap<>();
    recordsByInstrumentId.forEach((instrumentId, records) ->
        select(records)
            .ifPresent(selection -> selectionsByInstrumentId.put(instrumentId, selection)));
    return selectionsByInstrumentId;
  }

  public BigDecimal close() {
    if (latestWithClose != null) {
      return latestWithClose.close();
    }
    return latest.hasValidPreviousClose() ? latest.previousClose() : null;
  }

  public boolean usesHistoricalClose() {
    return latestWithClose != null && latestWithClose.id() != latest.id();
  }

  public boolean usesPreviousClose() {
    return latestWithClose == null && latest.hasValidPreviousClose();
  }

  public MarketData selectedRecord() {
    return latestWithClose == null ? latest : latestWithClose;
  }

  public BigDecimal dailyPriceChangeAt(LocalDate day) {
    if (latestWithClose == null || usesHistoricalClose()) {
      return null;
    }
    return latestWithClose.dailyPriceChangeAt(day);
  }
}
