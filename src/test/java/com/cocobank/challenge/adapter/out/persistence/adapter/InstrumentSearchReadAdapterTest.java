package com.cocobank.challenge.adapter.out.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cocobank.challenge.adapter.out.persistence.repository.InstrumentSearchProjection;
import com.cocobank.challenge.adapter.out.persistence.repository.InstrumentSearchRepository;
import com.cocobank.challenge.application.dto.InstrumentSearchField;
import com.cocobank.challenge.application.dto.InstrumentSearchResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class InstrumentSearchReadAdapterTest {

  @Mock private InstrumentSearchRepository repository;
  @Mock private InstrumentSearchProjection projection;
  @InjectMocks private InstrumentSearchReadAdapter adapter;

  @Test
  void passesSearchCriteriaAndMapsProjectionToResult() {
    LocalDate date = LocalDate.of(2024, 1, 15);
    when(repository.search("test", "TICKER")).thenReturn(List.of(projection));
    when(projection.getId()).thenReturn(7);
    when(projection.getTicker()).thenReturn("TEST");
    when(projection.getName()).thenReturn("Test");
    when(projection.getType()).thenReturn("ACCIONES");
    when(projection.getMarketPrice()).thenReturn(new BigDecimal("25.50"));
    when(projection.getCloseDate()).thenReturn(date);

    assertThat(adapter.search("test", InstrumentSearchField.TICKER))
        .containsExactly(
            new InstrumentSearchResult(
                7, "TEST", "Test", "ACCIONES", new BigDecimal("25.50"), date));
    verify(repository).search("test", "TICKER");
  }

  @Test
  void propagatesReadFailures() {
    DataAccessResourceFailureException failure =
        new DataAccessResourceFailureException("database unavailable");
    when(repository.search("test", "NAME")).thenThrow(failure);

    assertThatThrownBy(() -> adapter.search("test", InstrumentSearchField.NAME)).isSameAs(failure);
  }

  @Test
  void declaresReadOnlyTransaction() throws NoSuchMethodException {
    Transactional transaction =
        InstrumentSearchReadAdapter.class
            .getMethod("search", String.class, InstrumentSearchField.class)
            .getAnnotation(Transactional.class);
    assertThat(transaction).isNotNull();
    assertThat(transaction.readOnly()).isTrue();
  }
}
