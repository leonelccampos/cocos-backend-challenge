package com.cocobank.challenge.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cocobank.challenge.application.exception.PortfolioNotFoundException;
import com.cocobank.challenge.application.port.out.InstrumentPort;
import com.cocobank.challenge.application.port.out.MarketDataPort;
import com.cocobank.challenge.application.port.out.OrderPort;
import com.cocobank.challenge.application.port.out.UserPort;
import com.cocobank.challenge.domain.instrument.Instrument;
import com.cocobank.challenge.domain.market.MarketData;
import com.cocobank.challenge.domain.order.InvalidOrderDataException;
import com.cocobank.challenge.domain.order.Order;
import com.cocobank.challenge.domain.order.OrderSide;
import com.cocobank.challenge.domain.order.OrderStatus;
import com.cocobank.challenge.domain.order.OrderType;
import com.cocobank.challenge.domain.portfolio.Portfolio;
import com.cocobank.challenge.domain.user.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class GetPortfolioUseCaseTest {

  @Mock private UserPort userPort;
  @Mock private OrderPort orderPort;
  @Mock private InstrumentPort instrumentPort;
  @Mock private MarketDataPort marketDataPort;
  private GetPortfolioUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new GetPortfolioUseCase(userPort, orderPort, instrumentPort, marketDataPort);
  }

  @Test
  void readsEntityPortsForExecutedInstrumentOrdersOnly() {
    when(userPort.findById(7)).thenReturn(Optional.of(new User(7, "123456")));
    when(orderPort.findByUserId(7))
        .thenReturn(
            List.of(
                order(66, 1000, null, OrderSide.CASH_IN, OrderStatus.FILLED),
                order(31, 2, BigDecimal.TEN, OrderSide.BUY, OrderStatus.FILLED),
                order(32, null, null, OrderSide.BUY, OrderStatus.REJECTED),
                order(33, 1, BigDecimal.TEN, OrderSide.BUY, OrderStatus.NEW)));
    when(instrumentPort.findByIds(Set.of(31)))
        .thenReturn(List.of(new Instrument(31, "TICK", "Instrument", "ACCIONES")));
    when(marketDataPort.findByInstrumentIds(Set.of(31)))
        .thenReturn(List.of(new MarketData(1, 31, BigDecimal.TEN, null, LocalDate.of(2026, 1, 1))));

    Portfolio portfolio = useCase.execute(7, "123456");

    assertThat(portfolio.availableCash()).isEqualByComparingTo("970");
    assertThat(portfolio.totalAccountValue()).isEqualByComparingTo("1000");
    verify(orderPort).findByUserId(7);
    verify(instrumentPort).findByIds(Set.of(31));
    verify(marketDataPort).findByInstrumentIds(Set.of(31));
  }

  @Test
  void rejectsAbsentOrMismatchedAccountsBeforeReadingOrders() {
    when(userPort.findById(7)).thenReturn(Optional.of(new User(7, "123456")));
    assertThatThrownBy(() -> useCase.execute(7, "other"))
        .isInstanceOf(PortfolioNotFoundException.class);
    when(userPort.findById(7)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> useCase.execute(7, "123456"))
        .isInstanceOf(PortfolioNotFoundException.class);
    verifyNoInteractions(orderPort, instrumentPort, marketDataPort);
  }

  @Test
  void propagatesReadAndDomainErrors() {
    IllegalStateException failure = new IllegalStateException("read failure");
    when(userPort.findById(7)).thenThrow(failure).thenReturn(Optional.of(new User(7, "123456")));
    assertThatThrownBy(() -> useCase.execute(7, "123456")).isSameAs(failure);
    when(orderPort.findByUserId(7))
        .thenReturn(List.of(order(31, 2, null, OrderSide.BUY, OrderStatus.FILLED)));
    assertThatThrownBy(() -> useCase.execute(7, "123456"))
        .isInstanceOf(InvalidOrderDataException.class);
  }

  @Test
  void declaresReadOnlyRepeatableReadTransaction() throws NoSuchMethodException {
    Transactional transaction =
        GetPortfolioUseCase.class
            .getMethod("execute", int.class, String.class)
            .getAnnotation(Transactional.class);
    assertThat(transaction.readOnly()).isTrue();
    assertThat(transaction.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
  }

  @Test
  void logsFallbackAndUnavailablePricesWithoutFinancialOrAccountValues() {
    when(userPort.findById(7)).thenReturn(Optional.of(new User(7, "ACCT-007")));
    when(orderPort.findByUserId(7))
        .thenReturn(
            List.of(
                order(31, 2, BigDecimal.TEN, OrderSide.BUY, OrderStatus.FILLED),
                order(32, 2, BigDecimal.TEN, OrderSide.BUY, OrderStatus.FILLED)));
    when(instrumentPort.findByIds(Set.of(31, 32)))
        .thenReturn(
            List.of(
                new Instrument(31, "ONE", "One", "ACCIONES"),
                new Instrument(32, "TWO", "Two", "ACCIONES")));
    when(marketDataPort.findByInstrumentIds(Set.of(31, 32)))
        .thenReturn(
            List.of(
                new MarketData(
                    1, 31, BigDecimal.ZERO, new BigDecimal("123.45"), LocalDate.of(2026, 1, 1)),
                new MarketData(2, 32, BigDecimal.ZERO, null, LocalDate.of(2026, 1, 1))));
    Logger logger = (Logger) LoggerFactory.getLogger(GetPortfolioUseCase.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      useCase.execute(7, "ACCT-007");
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
    assertThat(appender.list)
        .filteredOn(event -> event.getLevel() == Level.WARN)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anyMatch(message ->
            message.contains("PREVIOUS_CLOSE")
                && message.contains("instrument_id=31")
                && message.contains("2026-01-01"))
        .anyMatch(message ->
            message.contains("unavailable")
                && message.contains("instrument_id=32")
                && message.contains("2026-01-01"))
        .noneMatch(message -> message.contains("ACCT-007") || message.contains("123.45"));
  }

  private Order order(
      int instrumentId, Integer size, BigDecimal price, OrderSide side, OrderStatus status) {
    return new Order(1, 7, instrumentId, side, OrderType.MARKET, size, price, status, null);
  }
}
