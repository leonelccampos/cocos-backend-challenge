package com.cocobank.challenge.instrumentsearch.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class InstrumentSearchCandidateTest {

  @Test
  void retainsBothPriceCandidatesIndependently() {
    InstrumentSearchCandidate candidate =
        new InstrumentSearchCandidate(
            7,
            "TICK",
            "Instrument",
            "ACCIONES",
            new BigDecimal("12.34"),
            LocalDate.of(2023, 7, 14),
            new BigDecimal("10.50"));

    assertThat(candidate.latestPositiveClose()).isEqualByComparingTo("12.34");
    assertThat(candidate.latestPositiveCloseDate()).isEqualTo(LocalDate.of(2023, 7, 14));
    assertThat(candidate.latestDatedPreviousClose()).isEqualByComparingTo("10.50");
  }

  @Test
  void retainsMissingCatalogFieldsAndPriceCandidatesAsNull() {
    InstrumentSearchCandidate candidate =
        new InstrumentSearchCandidate(7, null, null, null, null, null, null);

    assertThat(candidate.ticker()).isNull();
    assertThat(candidate.name()).isNull();
    assertThat(candidate.type()).isNull();
    assertThat(candidate.latestPositiveClose()).isNull();
    assertThat(candidate.latestPositiveCloseDate()).isNull();
    assertThat(candidate.latestDatedPreviousClose()).isNull();
  }
}
