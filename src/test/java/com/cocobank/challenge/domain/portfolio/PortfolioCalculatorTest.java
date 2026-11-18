package com.cocobank.challenge.domain.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cocobank.challenge.domain.instrument.Instrument;
import com.cocobank.challenge.domain.market.MarketData;
import com.cocobank.challenge.domain.market.MarketDataSelection;
import com.cocobank.challenge.domain.order.InvalidOrderDataException;
import com.cocobank.challenge.domain.order.Order;
import com.cocobank.challenge.domain.order.OrderSide;
import com.cocobank.challenge.domain.order.OrderStatus;
import com.cocobank.challenge.domain.order.OrderType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PortfolioCalculatorTest {

  private record MarketPrice(BigDecimal close, LocalDate date, BigDecimal previousClose) {}

  @Test
  void calculatesFilledHoldingsAndOmitsZeroQuantities() {
    PortfolioCalculator calculator = new PortfolioCalculator(LocalDate.of(2026, 1, 2));
    List<Order> movements =
        List.of(
            movement(31, 10, OrderSide.BUY, OrderStatus.FILLED),
            movement(31, 3, OrderSide.SELL, OrderStatus.FILLED),
            movement(31, 4, OrderSide.BUY, OrderStatus.NEW),
            movement(31, 2, OrderSide.SELL, OrderStatus.NEW),
            movement(31, 50, OrderSide.BUY, OrderStatus.CANCELLED),
            movement(31, 5, OrderSide.SELL, OrderStatus.REJECTED),
            movement(32, 2, OrderSide.BUY, OrderStatus.FILLED),
            movement(32, 2, OrderSide.SELL, OrderStatus.FILLED),
            movement(66, 100, OrderSide.CASH_IN, OrderStatus.FILLED));

    Portfolio portfolio =
        calculatePortfolio(
            calculator,
            movements,
            Map.of(
                31, new MarketPrice(BigDecimal.TEN, LocalDate.of(2026, 1, 1), null),
                32, new MarketPrice(BigDecimal.TEN, LocalDate.of(2026, 1, 1), null)));

    assertThat(portfolio.instruments())
        .singleElement()
        .satisfies(instrument -> {
          assertThat(instrument.instrumentId()).isEqualTo(31);
          assertThat(instrument.quantity()).isEqualTo(7);
          assertThat(instrument.availableQuantity()).isEqualTo(5);
        });
  }

  @Test
  void calculatesAvailableAndReservedCashFromFilledAndPendingOrders() {
    PortfolioCalculator calculator = new PortfolioCalculator(LocalDate.of(2026, 1, 2));
    List<Order> movements =
        List.of(
            movement(66, 1000, OrderSide.CASH_IN, OrderStatus.FILLED),
            movement(66, 125, OrderSide.CASH_OUT, OrderStatus.FILLED),
            movement(31, 4, BigDecimal.valueOf(15), OrderSide.BUY, OrderStatus.FILLED),
            movement(31, 2, BigDecimal.valueOf(20), OrderSide.SELL, OrderStatus.FILLED),
            movement(31, 3, BigDecimal.valueOf(30), OrderSide.BUY, OrderStatus.NEW),
            movement(31, 3, BigDecimal.valueOf(30), OrderSide.SELL, OrderStatus.NEW));

    Portfolio portfolio =
        calculatePortfolio(
            calculator,
            movements,
            Map.of(31, new MarketPrice(BigDecimal.TEN, LocalDate.of(2026, 1, 1), null)));

    assertThat(portfolio.availableCash()).isEqualByComparingTo("765");
    assertThat(portfolio.reservedCash()).isEqualByComparingTo("90");
  }

  @Test
  void releasesPendingReservationWhenOrderIsCancelledRejectedOrExecuted() {
    PortfolioCalculator calculator = new PortfolioCalculator(LocalDate.of(2026, 1, 2));
    Order cashIn = movement(66, 1000, OrderSide.CASH_IN, OrderStatus.FILLED);

    Portfolio pending =
        calculatePortfolio(
            calculator,
            List.of(
                cashIn, movement(31, 3, BigDecimal.valueOf(12), OrderSide.BUY, OrderStatus.NEW)),
            Map.of());
    Portfolio cancelled =
        calculatePortfolio(
            calculator,
            List.of(
                cashIn,
                movement(31, 3, BigDecimal.valueOf(12), OrderSide.BUY, OrderStatus.CANCELLED)),
            Map.of());
    Portfolio rejected =
        calculatePortfolio(
            calculator,
            List.of(
                cashIn,
                movement(31, 3, BigDecimal.valueOf(12), OrderSide.BUY, OrderStatus.REJECTED)),
            Map.of());
    Portfolio executed =
        calculatePortfolio(
            calculator,
            List.of(cashIn, movement(31, 3, BigDecimal.TEN, OrderSide.BUY, OrderStatus.FILLED)),
            Map.of(31, new MarketPrice(BigDecimal.TEN, LocalDate.of(2026, 1, 1), null)));

    assertThat(pending.availableCash()).isEqualByComparingTo("964");
    assertThat(pending.reservedCash()).isEqualByComparingTo("36");
    assertThat(cancelled.availableCash()).isEqualByComparingTo("1000");
    assertThat(cancelled.reservedCash()).isEqualByComparingTo("0");
    assertThat(rejected.availableCash()).isEqualByComparingTo("1000");
    assertThat(rejected.reservedCash()).isEqualByComparingTo("0");
    assertThat(executed.availableCash()).isEqualByComparingTo("970");
    assertThat(executed.reservedCash()).isEqualByComparingTo("0");
  }

  @Test
  void rejectsMissingPriceForCashImpactingOrders() {
    PortfolioCalculator calculator = new PortfolioCalculator(LocalDate.of(2026, 1, 2));
    List<Order> missingFilledPrice =
        List.of(movement(31, 2, null, OrderSide.BUY, OrderStatus.FILLED));
    List<Order> missingPendingPrice =
        List.of(movement(31, 2, null, OrderSide.BUY, OrderStatus.NEW));

    assertThatThrownBy(() -> calculatePortfolio(calculator, missingFilledPrice, Map.of()))
        .isInstanceOf(InvalidOrderDataException.class);
    assertThatThrownBy(() -> calculatePortfolio(calculator, missingPendingPrice, Map.of()))
        .isInstanceOf(InvalidOrderDataException.class);
  }

  @Test
  void calculatesMarketValueForPositivePrice() {
    PortfolioCalculator calculator = new PortfolioCalculator(LocalDate.of(2026, 1, 2));

    PortfolioInstrument instrument =
        calculateInstrument(
            calculator,
            4,
            BigDecimal.TEN,
            new MarketPrice(BigDecimal.valueOf(10), LocalDate.of(2026, 1, 1), null));

    assertThat(instrument.marketValue()).isEqualByComparingTo("40");
    assertThat(instrument.valuationStatus()).isEqualTo(ValuationStatus.AVAILABLE);
  }

  @Test
  void marksMissingZeroAndNegativePricesUnavailable() {
    PortfolioCalculator calculator = new PortfolioCalculator(LocalDate.of(2026, 1, 2));

    PortfolioInstrument missing = calculateInstrument(calculator, 4, BigDecimal.TEN, null);
    PortfolioInstrument zero =
        calculateInstrument(
            calculator,
            4,
            BigDecimal.TEN,
            new MarketPrice(BigDecimal.ZERO, LocalDate.of(2026, 1, 1), null));
    PortfolioInstrument negative =
        calculateInstrument(
            calculator,
            4,
            BigDecimal.TEN,
            new MarketPrice(BigDecimal.valueOf(-1), LocalDate.of(2026, 1, 1), null));

    assertUnavailable(missing);
    assertUnavailable(zero);
    assertUnavailable(negative);
  }

  @Test
  void calculatesAccumulatedReturnAndPercentageAfterPartialSale() {
    PortfolioCalculator calculator = new PortfolioCalculator(LocalDate.of(2026, 1, 2));
    List<Order> movements =
        List.of(
            movement(31, 6, BigDecimal.ONE, OrderSide.BUY, OrderStatus.FILLED),
            movement(31, 2, BigDecimal.valueOf(1.5), OrderSide.SELL, OrderStatus.FILLED));
    PortfolioInstrument instrument =
        calculatePortfolio(
                calculator,
                movements,
                Map.of(31, new MarketPrice(BigDecimal.valueOf(2), LocalDate.of(2026, 1, 1), null)))
            .instruments()
            .get(0);

    assertThat(instrument.accumulatedReturn()).isEqualByComparingTo("5");
    assertThat(instrument.accumulatedReturnPercentage()).isEqualByComparingTo("83.33333333");
  }

  @Test
  void leavesPercentageUnavailableWhenPurchaseCostIsZero() {
    PortfolioCalculator calculator = new PortfolioCalculator(LocalDate.of(2026, 1, 2));
    List<Order> movements =
        List.of(movement(31, 2, BigDecimal.ZERO, OrderSide.BUY, OrderStatus.FILLED));
    PortfolioInstrument instrument =
        calculatePortfolio(
                calculator,
                movements,
                Map.of(31, new MarketPrice(BigDecimal.valueOf(5), LocalDate.of(2026, 1, 1), null)))
            .instruments()
            .get(0);

    assertThat(instrument.accumulatedReturn()).isEqualByComparingTo("10");
    assertThat(instrument.accumulatedReturnPercentage()).isNull();
  }

  @Test
  void leavesPerformanceUnavailableWhenMarketValueIsUnavailable() {
    PortfolioCalculator calculator = new PortfolioCalculator(LocalDate.of(2026, 1, 2));
    Portfolio portfolio =
        calculatePortfolio(
            calculator,
            List.of(movement(31, 1, BigDecimal.TEN, OrderSide.BUY, OrderStatus.FILLED)),
            Map.of());

    assertThat(portfolio.instruments())
        .singleElement()
        .satisfies(instrument -> {
          assertThat(instrument.accumulatedReturn()).isNull();
          assertThat(instrument.accumulatedReturnPercentage()).isNull();
        });
  }

  @Test
  void calculatesTotalIncludingReservedCashAndExcludingUnavailableValuations() {
    PortfolioCalculator calculator = new PortfolioCalculator(LocalDate.of(2026, 1, 2));
    List<Order> movements =
        List.of(
            movement(66, 1000, OrderSide.CASH_IN, OrderStatus.FILLED),
            movement(31, 2, BigDecimal.TEN, OrderSide.BUY, OrderStatus.FILLED),
            movement(31, 1, BigDecimal.TEN, OrderSide.BUY, OrderStatus.NEW),
            movement(32, 1, BigDecimal.TEN, OrderSide.BUY, OrderStatus.FILLED));
    Map<Integer, MarketPrice> marketPricesByInstrumentId =
        Map.of(
            31, new MarketPrice(BigDecimal.valueOf(12), LocalDate.of(2026, 1, 1), null),
            32, new MarketPrice(null, LocalDate.of(2026, 1, 1), null));

    Portfolio portfolio = calculatePortfolio(calculator, movements, marketPricesByInstrumentId);

    assertThat(portfolio.availableCash()).isEqualByComparingTo("960");
    assertThat(portfolio.reservedCash()).isEqualByComparingTo("10");
    assertThat(portfolio.totalAccountValue()).isEqualByComparingTo("994");
    assertThat(portfolio.instruments())
        .extracting(PortfolioInstrument::instrumentId)
        .containsExactly(31, 32);
    assertThat(portfolio.instruments().get(0).marketValue()).isEqualByComparingTo("24");
    assertThat(portfolio.instruments().get(1).valuationStatus())
        .isEqualTo(ValuationStatus.UNAVAILABLE);
    assertThat(portfolio.instruments().get(1).marketValue()).isNull();
  }

  @Test
  void calculatesNegativePositionAndSubtractsItsValueFromTheAccountTotal() {
    PortfolioCalculator calculator = new PortfolioCalculator(LocalDate.of(2026, 1, 2));
    List<Order> movements =
        List.of(
            movement(66, 1000, OrderSide.CASH_IN, OrderStatus.FILLED),
            movement(31, 2, BigDecimal.valueOf(100), OrderSide.BUY, OrderStatus.FILLED),
            movement(31, 3, BigDecimal.valueOf(120), OrderSide.SELL, OrderStatus.FILLED),
            movement(31, 5, BigDecimal.valueOf(90), OrderSide.BUY, OrderStatus.NEW));

    Portfolio portfolio =
        calculatePortfolio(
            calculator,
            movements,
            Map.of(31, new MarketPrice(BigDecimal.valueOf(80), LocalDate.of(2026, 1, 1), null)));

    assertThat(portfolio.availableCash()).isEqualByComparingTo("710");
    assertThat(portfolio.reservedCash()).isEqualByComparingTo("450");
    assertThat(portfolio.totalAccountValue()).isEqualByComparingTo("1080");
    assertThat(portfolio.instruments())
        .singleElement()
        .satisfies(instrument -> {
          assertThat(instrument.quantity()).isEqualTo(-1);
          assertThat(instrument.availableQuantity()).isZero();
          assertThat(instrument.marketValue()).isEqualByComparingTo("-80");
          assertThat(instrument.accumulatedReturn()).isEqualByComparingTo("80");
          assertThat(instrument.accumulatedReturnPercentage()).isEqualByComparingTo("40");
          assertThat(instrument.valuationStatus()).isEqualTo(ValuationStatus.AVAILABLE);
        });
  }

  @Test
  void leavesNegativePositionPercentageUnavailableWithoutPurchases() {
    PortfolioCalculator calculator = new PortfolioCalculator(LocalDate.of(2026, 1, 2));
    Portfolio portfolio =
        calculatePortfolio(
            calculator,
            List.of(movement(31, 2, BigDecimal.TEN, OrderSide.SELL, OrderStatus.FILLED)),
            Map.of(31, new MarketPrice(BigDecimal.valueOf(8), LocalDate.of(2026, 1, 1), null)));

    assertThat(portfolio.instruments())
        .singleElement()
        .satisfies(instrument -> {
          assertThat(instrument.quantity()).isEqualTo(-2);
          assertThat(instrument.marketValue()).isEqualByComparingTo("-16");
          assertThat(instrument.accumulatedReturn()).isEqualByComparingTo("4");
          assertThat(instrument.accumulatedReturnPercentage()).isNull();
        });
  }

  @Test
  void keepsNegativePositionWhenPriceIsUnavailable() {
    PortfolioCalculator calculator = new PortfolioCalculator(LocalDate.of(2026, 1, 2));
    Portfolio portfolio =
        calculatePortfolio(
            calculator,
            List.of(movement(31, 2, BigDecimal.TEN, OrderSide.SELL, OrderStatus.FILLED)),
            Map.of());

    assertThat(portfolio.totalAccountValue()).isEqualByComparingTo("20");
    assertThat(portfolio.instruments())
        .singleElement()
        .satisfies(instrument -> {
          assertThat(instrument.quantity()).isEqualTo(-2);
          assertUnavailable(instrument);
          assertThat(instrument.accumulatedReturn()).isNull();
          assertThat(instrument.accumulatedReturnPercentage()).isNull();
        });
  }

  @Test
  void calculatesDailyReturnForUnchangedLongAndNegativePositions() {
    LocalDate today = LocalDate.of(2026, 1, 2);
    PortfolioCalculator calculator = new PortfolioCalculator(today);
    List<Order> movements =
        List.of(
            datedMovement(31, 10, OrderSide.BUY, OrderStatus.FILLED, today.minusDays(1)),
            datedMovement(31, 2, OrderSide.SELL, OrderStatus.FILLED, today.minusDays(1)),
            datedMovement(31, 1, OrderSide.BUY, OrderStatus.NEW, today),
            datedMovement(32, 3, OrderSide.SELL, OrderStatus.FILLED, today.minusDays(1)));
    Portfolio portfolio =
        calculatePortfolio(
            calculator,
            movements,
            Map.of(
                31, new MarketPrice(new BigDecimal("12"), today, new BigDecimal("10")),
                32, new MarketPrice(new BigDecimal("9"), today, new BigDecimal("10"))));

    assertThat(portfolio.instruments().get(0).dailyReturn()).isEqualByComparingTo("16");
    assertThat(portfolio.instruments().get(1).dailyReturn()).isEqualByComparingTo("3");
    Portfolio unchangedPrice =
        calculatePortfolio(
            calculator,
            List.of(datedMovement(31, 2, OrderSide.BUY, OrderStatus.FILLED, today.minusDays(1))),
            Map.of(31, new MarketPrice(BigDecimal.TEN, today, BigDecimal.TEN)));
    assertThat(unchangedPrice.instruments().get(0).dailyReturn()).isEqualByComparingTo("0");
  }

  @Test
  void dailyReturnIsUnavailableWhenAnExecutionDateIsTodayFutureOrUnknown() {
    LocalDate today = LocalDate.of(2026, 1, 2);
    PortfolioCalculator calculator = new PortfolioCalculator(today);
    MarketPrice price = new MarketPrice(new BigDecimal("12"), today, new BigDecimal("10"));

    for (LocalDate date : new LocalDate[] {today, today.plusDays(1), null}) {
      Portfolio portfolio =
          calculatePortfolio(
              calculator,
              List.of(datedMovement(31, 2, OrderSide.BUY, OrderStatus.FILLED, date)),
              Map.of(31, price));
      assertThat(portfolio.instruments().get(0).dailyReturn()).isNull();
    }
  }

  @Test
  void dailyReturnIsUnavailableForStaleMissingOrInvalidPrices() {
    LocalDate today = LocalDate.of(2026, 1, 2);
    PortfolioCalculator calculator = new PortfolioCalculator(today);
    List<Order> movements =
        List.of(datedMovement(31, 2, OrderSide.BUY, OrderStatus.FILLED, today.minusDays(1)));
    List<MarketPrice> prices =
        List.of(
            new MarketPrice(BigDecimal.TEN, today.minusDays(1), BigDecimal.ONE),
            new MarketPrice(BigDecimal.TEN, today.plusDays(1), BigDecimal.ONE),
            new MarketPrice(BigDecimal.TEN, today, null),
            new MarketPrice(BigDecimal.TEN, today, BigDecimal.ZERO),
            new MarketPrice(BigDecimal.TEN, today, BigDecimal.ONE.negate()),
            new MarketPrice(BigDecimal.ZERO, today, BigDecimal.ONE));

    for (MarketPrice price : prices) {
      assertThat(
              calculatePortfolio(calculator, movements, Map.of(31, price))
                  .instruments()
                  .get(0)
                  .dailyReturn())
          .isNull();
    }
    assertThat(
            calculatePortfolio(calculator, movements, Map.of()).instruments().get(0).dailyReturn())
        .isNull();
  }

  private Portfolio calculatePortfolio(
      PortfolioCalculator calculator,
      List<Order> orders,
      Map<Integer, MarketPrice> marketPricesByInstrumentId) {
    List<Instrument> instruments =
        orders.stream()
            .filter(Order::affectsExecutedPortfolio)
            .map(Order::instrumentId)
            .distinct()
            .map(id -> new Instrument(id, "TICK", "Instrument", "ACCIONES"))
            .toList();
    List<MarketData> marketData =
        marketPricesByInstrumentId.entrySet().stream()
            .map(entry ->
                new MarketData(
                    entry.getKey(),
                    entry.getKey(),
                    entry.getValue().close(),
                    entry.getValue().previousClose(),
                    entry.getValue().date()))
            .toList();
    return calculator.calculatePortfolio(
        orders, instruments, MarketDataSelection.selectByInstrumentId(marketData));
  }

  private Order datedMovement(
      int instrumentId, int size, OrderSide side, OrderStatus status, LocalDate date) {
    LocalDateTime dateTime = date == null ? null : date.atTime(12, 0);
    return order(instrumentId, size, BigDecimal.TEN, side, status, dateTime);
  }

  private PortfolioInstrument calculateInstrument(
      PortfolioCalculator calculator,
      int quantity,
      BigDecimal purchasePrice,
      MarketPrice marketPrice) {
    List<Order> movements =
        List.of(movement(31, quantity, purchasePrice, OrderSide.BUY, OrderStatus.FILLED));
    Map<Integer, MarketPrice> marketPricesByInstrumentId =
        marketPrice == null ? Map.of() : Map.of(31, marketPrice);
    return calculatePortfolio(calculator, movements, marketPricesByInstrumentId)
        .instruments()
        .get(0);
  }

  private void assertUnavailable(PortfolioInstrument instrument) {
    assertThat(instrument.marketValue()).isNull();
    assertThat(instrument.valuationStatus()).isEqualTo(ValuationStatus.UNAVAILABLE);
  }

  private Order movement(int instrumentId, int size, OrderSide side, OrderStatus status) {
    return order(instrumentId, size, BigDecimal.ONE, side, status, null);
  }

  private Order movement(
      int instrumentId, int size, BigDecimal price, OrderSide side, OrderStatus status) {
    return order(instrumentId, size, price, side, status, null);
  }

  private Order order(
      int instrumentId,
      int size,
      BigDecimal price,
      OrderSide side,
      OrderStatus status,
      LocalDateTime dateTime) {
    return new Order(1, 1, instrumentId, side, OrderType.MARKET, size, price, status, dateTime);
  }
}
