package com.cocobank.challenge.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cocobank.challenge.application.dto.CreateOrderData;
import com.cocobank.challenge.application.dto.IdempotentSubmission;
import com.cocobank.challenge.application.dto.SubmitOrderCommand;
import com.cocobank.challenge.application.exception.IdempotencyConflictException;
import com.cocobank.challenge.application.exception.OrderBadRequestException;
import com.cocobank.challenge.application.port.out.InstrumentPort;
import com.cocobank.challenge.application.port.out.MarketDataPort;
import com.cocobank.challenge.application.port.out.OrderIdempotencyPort;
import com.cocobank.challenge.application.port.out.OrderPort;
import com.cocobank.challenge.application.port.out.UserPort;
import com.cocobank.challenge.domain.instrument.Instrument;
import com.cocobank.challenge.domain.market.MarketData;
import com.cocobank.challenge.domain.order.Order;
import com.cocobank.challenge.domain.order.OrderRejectionReason;
import com.cocobank.challenge.domain.order.OrderSide;
import com.cocobank.challenge.domain.order.OrderStatus;
import com.cocobank.challenge.domain.order.OrderType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubmitOrderUseCaseTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-01-02T15:00:00Z"), ZoneId.of("UTC"));

  @Mock private UserPort userPort;
  @Mock private InstrumentPort instrumentPort;
  @Mock private MarketDataPort marketDataPort;
  @Mock private OrderPort orderPort;
  @Mock private OrderIdempotencyPort idempotencyPort;

  private SubmitOrderUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase =
        new SubmitOrderUseCase(
            userPort, instrumentPort, marketDataPort, orderPort, idempotencyPort, CLOCK);
    lenient().when(userPort.lock(1)).thenReturn(true);
    lenient().when(idempotencyPort.find(1, "request-1")).thenReturn(Optional.empty());
    lenient()
        .when(instrumentPort.find(31))
        .thenReturn(Optional.of(new Instrument(31, "TEST", "Test", "ACCIONES")));
    lenient()
        .when(orderPort.save(any()))
        .thenAnswer(invocation -> {
          CreateOrderData data = invocation.getArgument(0);
          return new Order(
              41,
              data.userId(),
              data.instrumentId(),
              data.side(),
              data.type(),
              data.size(),
              data.price(),
              data.status(),
              data.datetime());
        });
  }

  @Test
  void calculatesAmountAtMarketCloseAndFillsBuyOrder() {
    when(marketDataPort.lastMarketData(31))
        .thenReturn(Optional.of(marketData(new BigDecimal("100.00"))));
    when(orderPort.findByUserId(1))
        .thenReturn(List.of(movement(500, OrderSide.CASH_IN, OrderStatus.FILLED)));

    var result = useCase.execute(command(OrderType.MARKET, null, new BigDecimal("250.00"), null));

    assertThat(result.isRejected()).isFalse();
    assertThat(result.order().size()).isEqualTo(2);
    assertThat(result.order().price()).isEqualByComparingTo("100.00");
    assertThat(result.order().status()).isEqualTo(OrderStatus.FILLED);
  }

  @Test
  void rejectsAndPersistsWhenRequestedBuyAmountExceedsAvailableCash() {
    when(marketDataPort.lastMarketData(31))
        .thenReturn(Optional.of(marketData(new BigDecimal("100.00"))));
    when(orderPort.findByUserId(1))
        .thenReturn(List.of(movement(250, OrderSide.CASH_IN, OrderStatus.FILLED)));

    var result = useCase.execute(command(OrderType.MARKET, null, new BigDecimal("300.00"), null));

    assertThat(result.isRejected()).isTrue();
    assertThat(result.rejectionReason()).isEqualTo(OrderRejectionReason.INSUFFICIENT_CASH);
    assertThat(result.order().status()).isEqualTo(OrderStatus.REJECTED);
    verify(idempotencyPort).save(any(Integer.class), any(), any(), any(Integer.class), any());
  }

  @Test
  void rejectsMarketAmountWithoutCloseWithNullSizeAndDoesNotReadAvailability() {
    when(marketDataPort.lastMarketData(31)).thenReturn(Optional.empty());

    var result = useCase.execute(command(OrderType.MARKET, null, new BigDecimal("500.00"), null));

    assertThat(result.order().size()).isNull();
    assertThat(result.order().price()).isNull();
    assertThat(result.rejectionReason()).isEqualTo(OrderRejectionReason.MARKET_PRICE_UNAVAILABLE);
    verify(orderPort, never()).findByUserId(1);
  }

  @Test
  void rejectsMarketSizeWithoutCloseConservingTheRequestedSize() {
    when(marketDataPort.lastMarketData(31)).thenReturn(Optional.empty());

    var result = useCase.execute(command(OrderType.MARKET, 5, null, null));

    assertThat(result.order().size()).isEqualTo(5);
    assertThat(result.order().price()).isNull();
    assertThat(result.rejectionReason()).isEqualTo(OrderRejectionReason.MARKET_PRICE_UNAVAILABLE);
    verify(orderPort, never()).findByUserId(1);
  }

  @Test
  void validatesAtLeastOneWholeShareBeforeSolvency() {
    when(marketDataPort.lastMarketData(31))
        .thenReturn(Optional.of(marketData(new BigDecimal("100.00"))));

    assertThatThrownBy(
            () -> useCase.execute(command(OrderType.MARKET, null, new BigDecimal("99.99"), null)))
        .isInstanceOf(OrderBadRequestException.class);
    verify(orderPort, never()).findByUserId(1);
  }

  @Test
  void replayReturnsOriginalWithoutReevaluatingMutableData() {
    SubmitOrderCommand command = command(OrderType.LIMIT, 2, null, new BigDecimal("10.00"));
    Order original =
        new Order(
            41,
            1,
            31,
            OrderSide.BUY,
            OrderType.LIMIT,
            2,
            new BigDecimal("10"),
            OrderStatus.NEW,
            LocalDateTime.now(CLOCK));
    var result = new com.cocobank.challenge.application.dto.OrderSubmissionResult(original, null);
    when(idempotencyPort.find(1, "request-1"))
        .thenReturn(Optional.of(new IdempotentSubmission(command.fingerprint(), 201, result)));

    assertThat(useCase.execute(command).order()).isEqualTo(original);
    verify(instrumentPort, never()).find(31);
    verify(orderPort, never()).save(any());
  }

  @Test
  void reusedKeyWithDifferentBusinessDataConflicts() {
    SubmitOrderCommand command = command(OrderType.LIMIT, 2, null, new BigDecimal("10.00"));
    when(idempotencyPort.find(1, "request-1"))
        .thenReturn(Optional.of(new IdempotentSubmission("different", 201, null)));

    assertThatThrownBy(() -> useCase.execute(command))
        .isInstanceOf(IdempotencyConflictException.class);
  }

  private SubmitOrderCommand command(
      OrderType type, Integer size, BigDecimal amount, BigDecimal price) {
    return new SubmitOrderCommand(1, 31, OrderSide.BUY, type, size, amount, price, "request-1");
  }

  private Order movement(int size, OrderSide side, OrderStatus status) {
    return new Order(
        1, 1, 66, side, OrderType.MARKET, size, BigDecimal.ONE, status, LocalDateTime.now(CLOCK));
  }

  private MarketData marketData(BigDecimal close) {
    return new MarketData(1, 31, close, close, java.time.LocalDate.of(2026, 1, 1));
  }
}
