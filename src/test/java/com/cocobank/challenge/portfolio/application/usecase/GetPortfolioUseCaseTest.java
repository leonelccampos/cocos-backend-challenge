package com.cocobank.challenge.portfolio.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cocobank.challenge.portfolio.application.dto.PortfolioReadData;
import com.cocobank.challenge.portfolio.application.exception.PortfolioNotFoundException;
import com.cocobank.challenge.portfolio.application.port.out.PortfolioReadPort;
import com.cocobank.challenge.portfolio.domain.InvalidPortfolioDataException;
import com.cocobank.challenge.portfolio.domain.MarketPrice;
import com.cocobank.challenge.portfolio.domain.OrderMovement;
import com.cocobank.challenge.portfolio.domain.OrderSide;
import com.cocobank.challenge.portfolio.domain.OrderStatus;
import com.cocobank.challenge.portfolio.domain.Portfolio;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class GetPortfolioUseCaseTest {

  @Mock private PortfolioReadPort portfolioReadPort;

  private GetPortfolioUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new GetPortfolioUseCase(portfolioReadPort);
  }

  @Test
  void readsAndCalculatesPortfolioForRequestedAccount() {
    PortfolioReadData readData =
        new PortfolioReadData(
            List.of(movement(0, 1000, null, OrderSide.CASH_IN, OrderStatus.FILLED)), Map.of());
    when(portfolioReadPort.findByUserIdAndAccountNumber(7, "123456"))
        .thenReturn(Optional.of(readData));

    Portfolio portfolio = useCase.execute(7, "123456");

    assertThat(portfolio.availableCash()).isEqualByComparingTo("1000");
    assertThat(portfolio.totalAccountValue()).isEqualByComparingTo("1000");
    verify(portfolioReadPort).findByUserIdAndAccountNumber(7, "123456");
  }

  @Test
  void throwsPortfolioNotFoundWhenAccountPairDoesNotExist() {
    when(portfolioReadPort.findByUserIdAndAccountNumber(7, "123456")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(7, "123456"))
        .isInstanceOf(PortfolioNotFoundException.class);
  }

  @Test
  void propagatesReadErrors() {
    IllegalStateException readFailure = new IllegalStateException("read failure");
    when(portfolioReadPort.findByUserIdAndAccountNumber(7, "123456")).thenThrow(readFailure);

    assertThatThrownBy(() -> useCase.execute(7, "123456")).isSameAs(readFailure);
  }

  @Test
  void propagatesDomainErrors() {
    PortfolioReadData invalidData =
        new PortfolioReadData(
            List.of(movement(31, 2, null, OrderSide.BUY, OrderStatus.FILLED)), Map.of());
    when(portfolioReadPort.findByUserIdAndAccountNumber(7, "123456"))
        .thenReturn(Optional.of(invalidData));

    assertThatThrownBy(() -> useCase.execute(7, "123456"))
        .isInstanceOf(InvalidPortfolioDataException.class);
  }

  @Test
  void logsOnePrivacySafeWarningForEachUnavailableInstrument() {
    PortfolioReadData readData =
        new PortfolioReadData(
            List.of(
                movement(31, 2, BigDecimal.TEN, OrderSide.BUY, OrderStatus.FILLED),
                movement(31, 2, BigDecimal.TEN, OrderSide.BUY, OrderStatus.FILLED)),
            Map.of(31, new MarketPrice(BigDecimal.ZERO, LocalDate.of(2026, 1, 1), null)));
    when(portfolioReadPort.findByUserIdAndAccountNumber(7, "ACCT-007"))
        .thenReturn(Optional.of(readData));
    Logger logger = (Logger) LoggerFactory.getLogger(GetPortfolioUseCase.class);
    Level originalLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.setLevel(Level.WARN);
    logger.addAppender(appender);

    try {
      useCase.execute(7, "ACCT-007");
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(originalLevel);
      appender.stop();
    }

    assertThat(appender.list)
        .singleElement()
        .satisfies(event -> {
          assertThat(event.getLevel()).isEqualTo(Level.WARN);
          assertThat(event.getFormattedMessage())
              .contains("instrument_id=31", "market_date=2026-01-01")
              .doesNotContain("ACCT-007", "user_id=7", "40");
        });
  }

  private OrderMovement movement(
      int instrumentId, int size, BigDecimal price, OrderSide side, OrderStatus status) {
    return new OrderMovement(
        instrumentId, size, price, side, status, "TICK", "Instrument", "EQUITY", null);
  }
}
