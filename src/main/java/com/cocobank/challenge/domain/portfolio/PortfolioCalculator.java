package com.cocobank.challenge.domain.portfolio;

import com.cocobank.challenge.domain.instrument.Instrument;
import com.cocobank.challenge.domain.market.MarketDataSelection;
import com.cocobank.challenge.domain.order.Order;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public final class PortfolioCalculator {

  private final LocalDate today;

  public PortfolioCalculator(LocalDate today) {
    this.today = Objects.requireNonNull(today, "today");
  }

  public Portfolio calculatePortfolio(
      List<Order> orders,
      List<Instrument> instruments,
      Map<Integer, MarketDataSelection> selectionsByInstrumentId) {
    Map<Integer, Instrument> instrumentsById = indexById(instruments);
    Map<Integer, List<Order>> ordersByInstrumentId = groupByInstrumentId(orders);
    PortfolioCashSummary cashSummary = PortfolioCashSummary.calculate(orders);

    List<PortfolioInstrument> portfolioInstruments =
        ordersByInstrumentId.keySet().stream()
            .sorted()
            .map(instrumentId ->
                calculateInstrument(
                    instrumentId,
                    ordersByInstrumentId.get(instrumentId),
                    selectionsByInstrumentId.get(instrumentId),
                    instrumentsById.get(instrumentId)))
            .flatMap(Optional::stream)
            .toList();
    BigDecimal totalAccountValue = calculateTotalAccountValue(cashSummary, portfolioInstruments);

    return new Portfolio(
        cashSummary.available(), cashSummary.reserved(), totalAccountValue, portfolioInstruments);
  }

  private static Map<Integer, Instrument> indexById(List<Instrument> instruments) {
    return instruments.stream().collect(Collectors.toMap(Instrument::id, instrument -> instrument));
  }

  private static Map<Integer, List<Order>> groupByInstrumentId(List<Order> orders) {
    return orders.stream().collect(Collectors.groupingBy(Order::instrumentId));
  }

  private Optional<PortfolioInstrument> calculateInstrument(
      int instrumentId,
      List<Order> ordersForInstrument,
      MarketDataSelection selection,
      Instrument instrument) {
    InstrumentPosition position = InstrumentPosition.calculate(instrumentId, ordersForInstrument);
    if (position.quantity() == 0) {
      return Optional.empty();
    }
    if (instrument == null || instrument.ticker() == null || instrument.name() == null) {
      throw new InvalidPortfolioDataException("Instrument details are incomplete");
    }

    List<Order> executedMovements =
        ordersForInstrument.stream().filter(Order::affectsExecutedPortfolio).toList();
    BigDecimal close = selection == null ? null : selection.close();
    PortfolioValuation valuation = PortfolioValuation.calculate(position.quantity(), close);
    PortfolioPerformance performance = PortfolioPerformance.calculate(executedMovements, valuation);
    return Optional.of(
        new PortfolioInstrument(
            instrumentId,
            instrument.ticker(),
            instrument.name(),
            instrument.type(),
            position.quantity(),
            position.availableQuantity(),
            valuation.marketValue(),
            performance.accumulatedReturn(),
            performance.accumulatedReturnPercentage(),
            calculateDailyReturn(position.quantity(), selection, executedMovements),
            valuation.status()));
  }

  private BigDecimal calculateDailyReturn(
      int quantity, MarketDataSelection selection, List<Order> movements) {
    BigDecimal priceChange = selection == null ? null : selection.dailyPriceChangeAt(today);
    if (priceChange == null
        || movements.stream()
            .anyMatch(movement ->
                movement.datetime() == null
                    || !movement.datetime().toLocalDate().isBefore(today))) {
      return null;
    }
    return priceChange.multiply(BigDecimal.valueOf(quantity));
  }

  private BigDecimal calculateTotalAccountValue(
      PortfolioCashSummary cashSummary, List<PortfolioInstrument> instruments) {
    BigDecimal totalAccountValue = cashSummary.available().add(cashSummary.reserved());
    for (PortfolioInstrument instrument : instruments) {
      if (instrument.valuationStatus() == ValuationStatus.AVAILABLE) {
        totalAccountValue = totalAccountValue.add(instrument.marketValue());
      }
    }
    return totalAccountValue;
  }
}
