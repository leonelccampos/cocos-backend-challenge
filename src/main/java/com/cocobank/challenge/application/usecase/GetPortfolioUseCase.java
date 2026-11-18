package com.cocobank.challenge.application.usecase;

import com.cocobank.challenge.application.exception.PortfolioNotFoundException;
import com.cocobank.challenge.application.port.out.InstrumentPort;
import com.cocobank.challenge.application.port.out.MarketDataPort;
import com.cocobank.challenge.application.port.out.OrderPort;
import com.cocobank.challenge.application.port.out.UserPort;
import com.cocobank.challenge.domain.instrument.Instrument;
import com.cocobank.challenge.domain.market.MarketData;
import com.cocobank.challenge.domain.market.MarketDataSelection;
import com.cocobank.challenge.domain.order.Order;
import com.cocobank.challenge.domain.portfolio.Portfolio;
import com.cocobank.challenge.domain.portfolio.PortfolioCalculator;
import com.cocobank.challenge.domain.portfolio.PortfolioInstrument;
import com.cocobank.challenge.domain.portfolio.ValuationStatus;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetPortfolioUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(GetPortfolioUseCase.class);
  private static final ZoneId MARKET_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

  private final UserPort userPort;
  private final OrderPort orderPort;
  private final InstrumentPort instrumentPort;
  private final MarketDataPort marketDataPort;

  public GetPortfolioUseCase(
      UserPort userPort,
      OrderPort orderPort,
      InstrumentPort instrumentPort,
      MarketDataPort marketDataPort) {
    this.userPort = userPort;
    this.orderPort = orderPort;
    this.instrumentPort = instrumentPort;
    this.marketDataPort = marketDataPort;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Portfolio execute(int userId, String accountNumber) {
    requireMatchingAccount(userId, accountNumber);

    List<Order> orders = orderPort.findByUserId(userId);
    Set<Integer> instrumentIds = extractInstrumentIds(orders);
    List<Instrument> instruments = instrumentPort.findByIds(instrumentIds);
    List<MarketData> marketData = marketDataPort.findByInstrumentIds(instrumentIds);
    Map<Integer, MarketDataSelection> selectionsByInstrumentId =
        MarketDataSelection.selectByInstrumentId(marketData);
    logFallbackPrices(selectionsByInstrumentId);

    Portfolio portfolio =
        new PortfolioCalculator(LocalDate.now(MARKET_ZONE))
            .calculatePortfolio(orders, instruments, selectionsByInstrumentId);
    logUnavailableValuations(portfolio, selectionsByInstrumentId);
    return portfolio;
  }

  private void requireMatchingAccount(int userId, String accountNumber) {
    boolean accountExists =
        userPort.findById(userId).filter(user -> user.hasAccountNumber(accountNumber)).isPresent();
    if (!accountExists) {
      throw new PortfolioNotFoundException();
    }
  }

  private static Set<Integer> extractInstrumentIds(List<Order> orders) {
    return orders.stream()
        .filter(Order::affectsExecutedPortfolio)
        .map(Order::instrumentId)
        .collect(Collectors.toSet());
  }

  private void logFallbackPrices(Map<Integer, MarketDataSelection> selectionsByInstrumentId) {
    selectionsByInstrumentId.forEach(
        (instrumentId, selection) -> logFallbackPrice(instrumentId, selection));
  }

  private void logFallbackPrice(int instrumentId, MarketDataSelection selection) {
    String source = fallbackSource(selection);
    if (source != null) {
      LOGGER.warn(
          "portfolio.price.fallback instrument_id={} source={} record_date={}",
          instrumentId,
          source,
          selection.selectedRecord().date());
    }
  }

  private static String fallbackSource(MarketDataSelection selection) {
    if (selection.usesHistoricalClose()) {
      return "HISTORICAL_CLOSE";
    }
    if (selection.usesPreviousClose()) {
      return "PREVIOUS_CLOSE";
    }
    return null;
  }

  private void logUnavailableValuations(
      Portfolio portfolio, Map<Integer, MarketDataSelection> selectionsByInstrumentId) {
    portfolio.instruments().stream()
        .filter(instrument -> instrument.valuationStatus() == ValuationStatus.UNAVAILABLE)
        .forEach(instrument -> logUnavailableValuation(instrument, selectionsByInstrumentId));
  }

  private void logUnavailableValuation(
      PortfolioInstrument instrument, Map<Integer, MarketDataSelection> selectionsByInstrumentId) {
    MarketDataSelection selection = selectionsByInstrumentId.get(instrument.instrumentId());
    LOGGER.warn(
        "portfolio.valuation.unavailable instrument_id={} market_date={}",
        instrument.instrumentId(),
        selection == null ? null : selection.latest().date());
  }
}
