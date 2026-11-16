package com.cocobank.challenge.portfolio.adapter.out.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cocobank.challenge.portfolio.adapter.out.persistence.repository.MarketPriceProjection;
import com.cocobank.challenge.portfolio.adapter.out.persistence.repository.OrderMovementProjection;
import com.cocobank.challenge.portfolio.adapter.out.persistence.repository.PortfolioReadRepository;
import com.cocobank.challenge.portfolio.domain.InvalidPortfolioDataException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class PortfolioReadAdapterTest {

  @Mock private PortfolioReadRepository repository;

  @InjectMocks private PortfolioReadAdapter adapter;

  @Test
  void propagatesPersistenceErrorsWithoutReturningAnEmptyPortfolio() {
    DataAccessResourceFailureException failure =
        new DataAccessResourceFailureException("database unavailable");
    when(repository.existsByUserIdAndAccountNumber(1, "10001")).thenThrow(failure);

    assertThatThrownBy(() -> adapter.findByUserIdAndAccountNumber(1, "10001")).isSameAs(failure);
  }

  @Test
  void declaresReadOnlyRepeatableReadTransaction() throws NoSuchMethodException {
    Transactional transaction =
        PortfolioReadAdapter.class
            .getMethod("findByUserIdAndAccountNumber", int.class, String.class)
            .getAnnotation(Transactional.class);

    assertThat(transaction).isNotNull();
    assertThat(transaction.readOnly()).isTrue();
    assertThat(transaction.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
  }

  @Test
  void debugLogsReadCountsAndDurationsWithoutRequestValues() {
    when(repository.existsByUserIdAndAccountNumber(1, "10001")).thenReturn(true);
    when(repository.findOrderMovementsByUserId(1)).thenReturn(List.of());
    Logger logger = (Logger) LoggerFactory.getLogger(PortfolioReadAdapter.class);
    Level originalLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.setLevel(Level.DEBUG);
    logger.addAppender(appender);

    try {
      adapter.findByUserIdAndAccountNumber(1, "10001");
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(originalLevel);
      appender.stop();
    }

    assertThat(appender.list)
        .allSatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.DEBUG));
    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anyMatch(message ->
            message.contains("portfolio.read.account") && message.contains("duration_ms="));
    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anyMatch(message ->
            message.contains("portfolio.read.movements")
                && message.contains("rows=0")
                && message.contains("duration_ms="));
    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anyMatch(message ->
            message.contains("portfolio.read.market_prices")
                && message.contains("rows=0")
                && message.contains("duration_ms="));
    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .noneMatch(message -> message.contains("10001") || message.contains("user_id=1"));
  }

  @Test
  void warnsWhenUsingAPreviousCloseWithoutLoggingItsValue() {
    OrderMovementProjection movement = mock(OrderMovementProjection.class);
    MarketPriceProjection price = mock(MarketPriceProjection.class);
    when(repository.existsByUserIdAndAccountNumber(1, "10001")).thenReturn(true);
    when(repository.findOrderMovementsByUserId(1)).thenReturn(List.of(movement));
    when(movement.getInstrumentId()).thenReturn(7);
    when(movement.getSide()).thenReturn("BUY");
    when(movement.getStatus()).thenReturn("FILLED");
    when(repository.findLatestPricesByInstrumentIds(List.of(7))).thenReturn(List.of(price));
    when(price.getInstrumentId()).thenReturn(7);
    when(price.getClose()).thenReturn(new BigDecimal("123.45"));
    when(price.getDate()).thenReturn(LocalDate.parse("2023-07-14"));
    when(price.getFallbackSource()).thenReturn("PREVIOUS_CLOSE");
    Logger logger = (Logger) LoggerFactory.getLogger(PortfolioReadAdapter.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);

    try {
      var result = adapter.findByUserIdAndAccountNumber(1, "10001").orElseThrow();
      assertThat(result.latestPrices().get(7).close()).isEqualByComparingTo("123.45");
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }

    assertThat(appender.list)
        .filteredOn(event -> event.getLevel() == Level.WARN)
        .singleElement()
        .satisfies(event -> {
          assertThat(event.getFormattedMessage())
              .contains("PREVIOUS_CLOSE", "record_date=2023-07-14");
          assertThat(event.getFormattedMessage()).doesNotContain("123.45", "10001");
        });
  }

  @ParameterizedTest
  @ValueSource(strings = {"instrumentId", "size", "side", "status"})
  void rejectsOrderMovementProjectionWithMissingRequiredFields(String missingField) {
    OrderMovementProjection movement = mock(OrderMovementProjection.class);
    when(repository.existsByUserIdAndAccountNumber(1, "10001")).thenReturn(true);
    when(repository.findOrderMovementsByUserId(1)).thenReturn(List.of(movement));
    when(movement.getInstrumentId()).thenReturn("instrumentId".equals(missingField) ? null : 7);
    if (!"instrumentId".equals(missingField)) {
      when(movement.getSize()).thenReturn("size".equals(missingField) ? null : 10);
    }
    if (!"instrumentId".equals(missingField) && !"size".equals(missingField)) {
      when(movement.getSide()).thenReturn("side".equals(missingField) ? null : "BUY");
    }
    if ("status".equals(missingField)) {
      when(movement.getStatus()).thenReturn(null);
    }

    assertThatThrownBy(() -> adapter.findByUserIdAndAccountNumber(1, "10001"))
        .isInstanceOf(InvalidPortfolioDataException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"side", "status"})
  void rejectsOrderMovementProjectionWithUnsupportedEnumValues(String invalidField) {
    OrderMovementProjection movement = mock(OrderMovementProjection.class);
    when(repository.existsByUserIdAndAccountNumber(1, "10001")).thenReturn(true);
    when(repository.findOrderMovementsByUserId(1)).thenReturn(List.of(movement));
    when(movement.getInstrumentId()).thenReturn(7);
    when(movement.getSize()).thenReturn(10);
    when(movement.getSide()).thenReturn("side".equals(invalidField) ? "UNKNOWN" : "BUY");
    if ("status".equals(invalidField)) {
      when(movement.getStatus()).thenReturn("UNKNOWN");
    }

    assertThatThrownBy(() -> adapter.findByUserIdAndAccountNumber(1, "10001"))
        .isInstanceOf(InvalidPortfolioDataException.class)
        .hasMessage("Order movement has an unsupported " + invalidField);
  }
}
