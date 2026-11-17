package com.cocobank.challenge.instrumentsearch.adapter.out.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cocobank.challenge.instrumentsearch.adapter.out.persistence.repository.InstrumentSearchProjection;
import com.cocobank.challenge.instrumentsearch.adapter.out.persistence.repository.InstrumentSearchRepository;
import com.cocobank.challenge.instrumentsearch.domain.InstrumentSearchCandidate;
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
  void mapsProjectionToCandidate() {
    LocalDate date = LocalDate.of(2024, 1, 15);
    when(repository.findAllInstrumentSearchCandidates()).thenReturn(List.of(projection));
    when(projection.getId()).thenReturn(7);
    when(projection.getTicker()).thenReturn("TEST");
    when(projection.getName()).thenReturn("Test");
    when(projection.getType()).thenReturn("ACCIONES");
    when(projection.getLatestPositiveClose()).thenReturn(new BigDecimal("25.50"));
    when(projection.getLatestPositiveCloseDate()).thenReturn(date);
    when(projection.getLatestDatedPreviousClose()).thenReturn(new BigDecimal("20.00"));

    assertThat(adapter.findAllInstrumentSearchCandidates())
        .containsExactly(
            new InstrumentSearchCandidate(
                7,
                "TEST",
                "Test",
                "ACCIONES",
                new BigDecimal("25.50"),
                date,
                new BigDecimal("20.00")));
    verify(repository).findAllInstrumentSearchCandidates();
  }

  @Test
  void propagatesReadFailures() {
    DataAccessResourceFailureException failure =
        new DataAccessResourceFailureException("database unavailable");
    when(repository.findAllInstrumentSearchCandidates()).thenThrow(failure);

    assertThatThrownBy(() -> adapter.findAllInstrumentSearchCandidates()).isSameAs(failure);
  }

  @Test
  void declaresReadOnlyTransaction() throws NoSuchMethodException {
    Transactional transaction =
        InstrumentSearchReadAdapter.class
            .getMethod("findAllInstrumentSearchCandidates")
            .getAnnotation(Transactional.class);
    assertThat(transaction).isNotNull();
    assertThat(transaction.readOnly()).isTrue();
  }
}
