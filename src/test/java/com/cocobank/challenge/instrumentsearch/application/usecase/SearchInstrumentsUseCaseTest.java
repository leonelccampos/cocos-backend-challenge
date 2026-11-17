package com.cocobank.challenge.instrumentsearch.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cocobank.challenge.instrumentsearch.application.exception.InstrumentSearchNotFoundException;
import com.cocobank.challenge.instrumentsearch.application.port.out.InstrumentSearchReadPort;
import com.cocobank.challenge.instrumentsearch.domain.InstrumentSearchCandidate;
import com.cocobank.challenge.instrumentsearch.domain.InstrumentSearchField;
import com.cocobank.challenge.instrumentsearch.domain.InstrumentSearchResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchInstrumentsUseCaseTest {

  @Mock private InstrumentSearchReadPort readPort;

  private SearchInstrumentsUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new SearchInstrumentsUseCase(readPort);
  }

  @Test
  void readsCandidatesAndReturnsMatchingStocksWithTheirPricesInOrder() {
    LocalDate date = LocalDate.of(2026, 1, 15);
    when(readPort.findAllInstrumentSearchCandidates())
        .thenReturn(
            List.of(
                new InstrumentSearchCandidate(2, "ZZZ", "Camión", "ACCIONES", null, null, null),
                new InstrumentSearchCandidate(
                    1, "AAA", "Camion", "ACCIONES", new BigDecimal("25.50"), date, null),
                new InstrumentSearchCandidate(3, "BBB", "Camion", "MONEDA", null, null, null),
                new InstrumentSearchCandidate(4, "CCC", "Other", "ACCIONES", null, null, null)));

    assertThat(useCase.execute("  CAMION ", InstrumentSearchField.NAME))
        .containsExactly(
            new InstrumentSearchResult(
                1, "AAA", "Camion", "ACCIONES", new BigDecimal("25.50"), date),
            new InstrumentSearchResult(2, "ZZZ", "Camión", "ACCIONES", null, null));
    verify(readPort).findAllInstrumentSearchCandidates();
  }

  @Test
  void throwsNotFoundWhenCandidatesDoNotMatchSelectedField() {
    when(readPort.findAllInstrumentSearchCandidates())
        .thenReturn(
            List.of(
                new InstrumentSearchCandidate(1, "AAA", "Match", "ACCIONES", null, null, null)));

    assertThatThrownBy(() -> useCase.execute("match", InstrumentSearchField.TICKER))
        .isInstanceOf(InstrumentSearchNotFoundException.class);
    verify(readPort).findAllInstrumentSearchCandidates();
  }

  @Test
  void throwsNotFoundWhenCatalogIsEmpty() {
    when(readPort.findAllInstrumentSearchCandidates()).thenReturn(List.of());

    assertThatThrownBy(() -> useCase.execute("anything", InstrumentSearchField.NAME))
        .isInstanceOf(InstrumentSearchNotFoundException.class);
  }

  @Test
  void propagatesReadErrors() {
    IllegalStateException failure = new IllegalStateException("read failure");
    when(readPort.findAllInstrumentSearchCandidates()).thenThrow(failure);

    assertThatThrownBy(() -> useCase.execute("abc", InstrumentSearchField.TICKER))
        .isSameAs(failure);
    verify(readPort).findAllInstrumentSearchCandidates();
  }
}
