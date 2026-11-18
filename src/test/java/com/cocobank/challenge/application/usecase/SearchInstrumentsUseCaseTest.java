package com.cocobank.challenge.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cocobank.challenge.application.dto.InstrumentSearchField;
import com.cocobank.challenge.application.dto.InstrumentSearchResult;
import com.cocobank.challenge.application.exception.InstrumentSearchNotFoundException;
import com.cocobank.challenge.application.port.out.InstrumentSearchReadPort;
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
  void trimsQueryAndReturnsSearchResults() {
    LocalDate date = LocalDate.of(2026, 1, 15);
    when(readPort.search("CAMION", InstrumentSearchField.NAME))
        .thenReturn(
            List.of(
                new InstrumentSearchResult(
                    1, "AAA", "Camion", "ACCIONES", new BigDecimal("25.50"), date),
                new InstrumentSearchResult(2, "ZZZ", "Camión", "ACCIONES", null, null)));

    assertThat(useCase.execute("  CAMION ", InstrumentSearchField.NAME))
        .containsExactly(
            new InstrumentSearchResult(
                1, "AAA", "Camion", "ACCIONES", new BigDecimal("25.50"), date),
            new InstrumentSearchResult(2, "ZZZ", "Camión", "ACCIONES", null, null));
    verify(readPort).search("CAMION", InstrumentSearchField.NAME);
  }

  @Test
  void throwsNotFoundWhenCandidatesDoNotMatchSelectedField() {
    when(readPort.search("match", InstrumentSearchField.TICKER)).thenReturn(List.of());

    assertThatThrownBy(() -> useCase.execute("match", InstrumentSearchField.TICKER))
        .isInstanceOf(InstrumentSearchNotFoundException.class);
    verify(readPort).search("match", InstrumentSearchField.TICKER);
  }

  @Test
  void throwsNotFoundWhenCatalogIsEmpty() {
    when(readPort.search("anything", InstrumentSearchField.NAME)).thenReturn(List.of());

    assertThatThrownBy(() -> useCase.execute("anything", InstrumentSearchField.NAME))
        .isInstanceOf(InstrumentSearchNotFoundException.class);
  }

  @Test
  void propagatesReadErrors() {
    IllegalStateException failure = new IllegalStateException("read failure");
    when(readPort.search("abc", InstrumentSearchField.TICKER)).thenThrow(failure);

    assertThatThrownBy(() -> useCase.execute("abc", InstrumentSearchField.TICKER))
        .isSameAs(failure);
    verify(readPort).search("abc", InstrumentSearchField.TICKER);
  }
}
