package com.cocobank.challenge.portfolio.application.usecase;

import com.cocobank.challenge.portfolio.application.dto.PortfolioReadData;
import com.cocobank.challenge.portfolio.application.exception.PortfolioNotFoundException;
import com.cocobank.challenge.portfolio.application.port.out.PortfolioReadPort;
import com.cocobank.challenge.portfolio.domain.MarketPrice;
import com.cocobank.challenge.portfolio.domain.Portfolio;
import com.cocobank.challenge.portfolio.domain.PortfolioCalculator;
import com.cocobank.challenge.portfolio.domain.PortfolioInstrument;
import com.cocobank.challenge.portfolio.domain.ValuationStatus;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public final class GetPortfolioUseCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(GetPortfolioUseCase.class);

  private final PortfolioReadPort portfolioReadPort;
  private static final ZoneId MARKET_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

  public GetPortfolioUseCase(PortfolioReadPort portfolioReadPort) {
    this.portfolioReadPort = portfolioReadPort;
  }

  public Portfolio execute(int userId, String accountNumber) {
    PortfolioReadData readData =
        portfolioReadPort
            .findByUserIdAndAccountNumber(userId, accountNumber)
            .orElseThrow(PortfolioNotFoundException::new);
    Portfolio portfolio =
        new PortfolioCalculator(LocalDate.now(MARKET_ZONE))
            .calculatePortfolio(readData.orderMovements(), readData.latestPrices());
    logUnavailableValuations(portfolio, readData);
    return portfolio;
  }

  private void logUnavailableValuations(Portfolio portfolio, PortfolioReadData readData) {
    portfolio.instruments().stream()
        .filter(instrument -> instrument.valuationStatus() == ValuationStatus.UNAVAILABLE)
        .forEach(instrument -> logUnavailableValuation(instrument, readData));
  }

  private void logUnavailableValuation(PortfolioInstrument instrument, PortfolioReadData readData) {
    MarketPrice marketPrice = readData.latestPrices().get(instrument.instrumentId());
    LOGGER.warn(
        "portfolio.valuation.unavailable instrument_id={} market_date={}",
        instrument.instrumentId(),
        marketPrice == null ? null : marketPrice.date());
  }
}
