package com.cocobank.challenge.portfolio.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class PortfolioCalculator {

  private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
  private static final int PERCENTAGE_SCALE = 8;

  private final LocalDate today;

  public PortfolioCalculator(LocalDate today) {
    this.today = Objects.requireNonNull(today, "today");
  }

  public Portfolio calculatePortfolio(
      List<OrderMovement> movements, Map<Integer, MarketPrice> latestPrices) {
    PortfolioCashSummary cashSummary = calculateCashSummary(movements);
    Map<Integer, Integer> holdings = calculateHoldings(movements);
    Map<Integer, List<OrderMovement>> movementsByInstrument =
        groupFilledInstrumentMovements(movements);
    List<PortfolioInstrument> instruments =
        new TreeMap<>(holdings)
            .entrySet().stream()
                .map(holding ->
                    calculateInstrument(
                        holding.getKey(), holding.getValue(), movementsByInstrument, latestPrices))
                .toList();
    BigDecimal totalAccountValue = calculateTotalAccountValue(cashSummary, instruments);

    return new Portfolio(
        cashSummary.available(), cashSummary.reserved(), totalAccountValue, instruments);
  }

  private Map<Integer, Integer> calculateHoldings(List<OrderMovement> movements) {
    Map<Integer, Integer> quantitiesByInstrument = new HashMap<>();
    for (OrderMovement movement : movements) {
      if (movement.status() != OrderStatus.FILLED) {
        continue;
      }

      if (movement.side() == OrderSide.BUY) {
        quantitiesByInstrument.merge(movement.instrumentId(), movement.size(), Math::addExact);
      } else if (movement.side() == OrderSide.SELL) {
        quantitiesByInstrument.merge(
            movement.instrumentId(), Math.negateExact(movement.size()), Math::addExact);
      }
    }

    quantitiesByInstrument.values().removeIf(quantity -> quantity == 0);
    return Map.copyOf(quantitiesByInstrument);
  }

  private PortfolioCashSummary calculateCashSummary(List<OrderMovement> movements) {
    BigDecimal available = BigDecimal.ZERO;
    BigDecimal reserved = BigDecimal.ZERO;
    for (OrderMovement movement : movements) {
      if (movement.status() == OrderStatus.FILLED) {
        switch (movement.side()) {
          case CASH_IN -> available = available.add(BigDecimal.valueOf(movement.size()));
          case CASH_OUT -> available = available.subtract(BigDecimal.valueOf(movement.size()));
          case BUY -> available = available.subtract(orderValue(movement));
          case SELL -> available = available.add(orderValue(movement));
        }
      } else if (movement.status() == OrderStatus.NEW && movement.side() == OrderSide.BUY) {
        BigDecimal orderValue = orderValue(movement);
        available = available.subtract(orderValue);
        reserved = reserved.add(orderValue);
      }
    }

    return new PortfolioCashSummary(available, reserved);
  }

  private PortfolioValuation calculateValuation(int quantity, BigDecimal close) {
    if (quantity == 0) {
      throw new IllegalArgumentException("quantity must not be zero");
    }
    if (close == null || close.signum() <= 0) {
      return new PortfolioValuation(null, ValuationStatus.UNAVAILABLE);
    }
    return new PortfolioValuation(
        close.multiply(BigDecimal.valueOf(quantity)), ValuationStatus.AVAILABLE);
  }

  private PortfolioPerformance calculatePerformance(
      List<OrderMovement> movements, PortfolioValuation valuation) {
    BigDecimal purchaseCost = BigDecimal.ZERO;
    BigDecimal saleProceeds = BigDecimal.ZERO;
    for (OrderMovement movement : movements) {
      if (movement.status() != OrderStatus.FILLED) {
        continue;
      }
      if (movement.side() == OrderSide.BUY) {
        purchaseCost = purchaseCost.add(orderValue(movement));
      } else if (movement.side() == OrderSide.SELL) {
        saleProceeds = saleProceeds.add(orderValue(movement));
      }
    }

    if (valuation.status() == ValuationStatus.UNAVAILABLE) {
      return new PortfolioPerformance(null, null);
    }

    BigDecimal accumulatedReturn = saleProceeds.add(valuation.marketValue()).subtract(purchaseCost);
    BigDecimal accumulatedReturnPercentage =
        purchaseCost.signum() == 0
            ? null
            : accumulatedReturn
                .multiply(ONE_HUNDRED)
                .divide(purchaseCost, PERCENTAGE_SCALE, RoundingMode.HALF_EVEN);
    return new PortfolioPerformance(accumulatedReturn, accumulatedReturnPercentage);
  }

  private Map<Integer, List<OrderMovement>> groupFilledInstrumentMovements(
      List<OrderMovement> movements) {
    Map<Integer, List<OrderMovement>> movementsByInstrument = new HashMap<>();
    for (OrderMovement movement : movements) {
      if (movement.status() == OrderStatus.FILLED
          && (movement.side() == OrderSide.BUY || movement.side() == OrderSide.SELL)) {
        movementsByInstrument
            .computeIfAbsent(movement.instrumentId(), ignored -> new ArrayList<>())
            .add(movement);
      }
    }
    return movementsByInstrument;
  }

  private PortfolioInstrument calculateInstrument(
      int instrumentId,
      int quantity,
      Map<Integer, List<OrderMovement>> movementsByInstrument,
      Map<Integer, MarketPrice> latestPrices) {
    List<OrderMovement> instrumentMovements = movementsByInstrument.get(instrumentId);
    if (instrumentMovements == null || instrumentMovements.isEmpty()) {
      throw new InvalidPortfolioDataException("Filled instrument movements are missing");
    }
    OrderMovement instrumentDetails = instrumentMovements.get(0);
    if (instrumentDetails.ticker() == null
        || instrumentDetails.name() == null
        || instrumentDetails.instrumentType() == null) {
      throw new InvalidPortfolioDataException("Instrument details are incomplete");
    }

    MarketPrice marketPrice = latestPrices.get(instrumentId);
    BigDecimal close = marketPrice == null ? null : marketPrice.close();
    PortfolioValuation valuation = calculateValuation(quantity, close);
    PortfolioPerformance performance = calculatePerformance(instrumentMovements, valuation);
    return new PortfolioInstrument(
        instrumentId,
        instrumentDetails.ticker(),
        instrumentDetails.name(),
        instrumentDetails.instrumentType(),
        quantity,
        valuation.marketValue(),
        performance.accumulatedReturn(),
        performance.accumulatedReturnPercentage(),
        calculateDailyReturn(quantity, marketPrice, instrumentMovements),
        valuation.status());
  }

  private BigDecimal calculateDailyReturn(
      int quantity, MarketPrice marketPrice, List<OrderMovement> movements) {
    if (marketPrice == null
        || !today.equals(marketPrice.date())
        || marketPrice.close() == null
        || marketPrice.close().signum() <= 0
        || marketPrice.previousClose() == null
        || marketPrice.previousClose().signum() <= 0
        || movements.stream()
            .anyMatch(movement ->
                movement.dateTime() == null
                    || !movement.dateTime().toLocalDate().isBefore(today))) {
      return null;
    }
    return marketPrice
        .close()
        .subtract(marketPrice.previousClose())
        .multiply(BigDecimal.valueOf(quantity));
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

  private BigDecimal orderValue(OrderMovement movement) {
    if (movement.price() == null) {
      throw new InvalidPortfolioDataException("A price is required for a cash-impacting order");
    }
    return movement.price().multiply(BigDecimal.valueOf(movement.size()));
  }
}
