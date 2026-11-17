package com.cocobank.challenge.instrumentsearch.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InstrumentSearchPolicyTest {

  @Test
  void matchesTickerSubstringsIgnoringCaseAccentsAndSurroundingSpaces() {
    List<InstrumentSearchCandidate> candidates =
        List.of(
            candidate(2, "XABZ", "Other", "ACCIONES"),
            candidate(1, "ÁBC", "Other", "ACCIONES"),
            candidate(3, "OTHER", "AB in name", "ACCIONES"),
            candidate(4, "AB", "Currency", "MONEDA"),
            candidate(5, "AB", "Unknown type", null),
            candidate(6, null, "AB in name", "ACCIONES"));

    assertThat(
            InstrumentSearchPolicy.filterAndSort(
                candidates, "  áB  ", InstrumentSearchField.TICKER))
        .extracting(InstrumentSearchCandidate::id)
        .containsExactly(2, 1);
  }

  @Test
  void matchesNameSubstringsIncludingDecomposedAccentsAndSkipsNullNames() {
    List<InstrumentSearchCandidate> candidates =
        List.of(
            candidate(1, "ONE", "Transporte Camio\u0301n", "ACCIONES"),
            candidate(2, "TWO", "CAMIÓN S.A.", "ACCIONES"),
            candidate(3, "CAMION", null, "ACCIONES"),
            candidate(4, "FOUR", "Camion", "MONEDA"));

    assertThat(
            InstrumentSearchPolicy.filterAndSort(
                candidates, "  camión  ", InstrumentSearchField.NAME))
        .extracting(InstrumentSearchCandidate::id)
        .containsExactly(1, 2);
  }

  @Test
  void returnsAllMatchesOrderedByTickerThenIdWithNullTickersLast() {
    List<InstrumentSearchCandidate> candidates =
        new ArrayList<>(
            List.of(
                candidate(8, null, "Company", "ACCIONES"),
                candidate(4, "ZZZ", "Company", "ACCIONES"),
                candidate(3, "AAA", "Company", "ACCIONES"),
                candidate(2, null, "Company", "ACCIONES"),
                candidate(1, "AAA", "Company", "ACCIONES")));
    List<InstrumentSearchCandidate> original = List.copyOf(candidates);

    List<InstrumentSearchCandidate> results =
        InstrumentSearchPolicy.filterAndSort(candidates, "company", InstrumentSearchField.NAME);

    assertThat(results).extracting(InstrumentSearchCandidate::id).containsExactly(1, 3, 4, 2, 8);
    assertThat(candidates).containsExactlyElementsOf(original);
    assertThatExceptionOfType(UnsupportedOperationException.class)
        .isThrownBy(() -> results.add(candidates.getFirst()));
  }

  @Test
  void selectsPositiveDatedCloseBeforePreviousCloseAndMapsInstrument() {
    LocalDate closeDate = LocalDate.of(2026, 1, 15);
    InstrumentSearchCandidate candidate =
        pricedCandidate(1, new BigDecimal("125.50"), closeDate, new BigDecimal("140.00"));

    assertThat(
            InstrumentSearchPolicy.search(List.of(candidate), "abc", InstrumentSearchField.TICKER))
        .containsExactly(
            new InstrumentSearchResult(
                1, "ABC", "Company", "ACCIONES", new BigDecimal("125.50"), closeDate));
  }

  @Test
  void searchMapsOnlyMatchingStocksInTickerAndIdOrder() {
    List<InstrumentSearchCandidate> candidates =
        List.of(
            candidate(3, "ZZZ", "Company", "ACCIONES"),
            candidate(2, "AAA", "Company", "ACCIONES"),
            candidate(1, "AAA", "Company", "ACCIONES"),
            candidate(4, "BBB", "Company", "MONEDA"),
            candidate(5, "CCC", "Other", "ACCIONES"));

    assertThat(InstrumentSearchPolicy.search(candidates, "company", InstrumentSearchField.NAME))
        .extracting(InstrumentSearchResult::id)
        .containsExactly(1, 2, 3);
  }

  @Test
  void fallsBackToPositivePreviousCloseWithoutAnExactDate() {
    LocalDate closeDate = LocalDate.of(2026, 1, 15);
    List<InstrumentSearchCandidate> candidates =
        List.of(
            pricedCandidate(1, null, null, new BigDecimal("10.50")),
            pricedCandidate(2, BigDecimal.ZERO, closeDate, new BigDecimal("20.50")),
            pricedCandidate(3, new BigDecimal("-1"), closeDate, new BigDecimal("30.50")),
            pricedCandidate(4, new BigDecimal("40"), null, new BigDecimal("40.50")));

    assertThat(InstrumentSearchPolicy.search(candidates, "abc", InstrumentSearchField.TICKER))
        .extracting(InstrumentSearchResult::marketPrice, InstrumentSearchResult::closeDate)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(new BigDecimal("10.50"), null),
            org.assertj.core.groups.Tuple.tuple(new BigDecimal("20.50"), null),
            org.assertj.core.groups.Tuple.tuple(new BigDecimal("30.50"), null),
            org.assertj.core.groups.Tuple.tuple(new BigDecimal("40.50"), null));
  }

  @Test
  void leavesPriceAndDateNullWhenNeitherPriceIsUsable() {
    LocalDate closeDate = LocalDate.of(2026, 1, 15);
    List<InstrumentSearchCandidate> candidates =
        List.of(
            pricedCandidate(1, null, null, null),
            pricedCandidate(2, BigDecimal.ZERO, closeDate, BigDecimal.ZERO),
            pricedCandidate(3, new BigDecimal("-1"), closeDate, new BigDecimal("-2")),
            pricedCandidate(4, new BigDecimal("1"), null, null));

    assertThat(InstrumentSearchPolicy.search(candidates, "abc", InstrumentSearchField.TICKER))
        .extracting(InstrumentSearchResult::marketPrice, InstrumentSearchResult::closeDate)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(null, null),
            org.assertj.core.groups.Tuple.tuple(null, null),
            org.assertj.core.groups.Tuple.tuple(null, null),
            org.assertj.core.groups.Tuple.tuple(null, null));
  }

  private static InstrumentSearchCandidate pricedCandidate(
      int id, BigDecimal close, LocalDate closeDate, BigDecimal previousClose) {
    return new InstrumentSearchCandidate(
        id, "ABC", "Company", "ACCIONES", close, closeDate, previousClose);
  }

  private static InstrumentSearchCandidate candidate(
      int id, String ticker, String name, String type) {
    return new InstrumentSearchCandidate(id, ticker, name, type, null, null, null);
  }
}
