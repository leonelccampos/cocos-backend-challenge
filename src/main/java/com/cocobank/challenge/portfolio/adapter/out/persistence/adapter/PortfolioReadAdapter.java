package com.cocobank.challenge.portfolio.adapter.out.persistence.adapter;

import com.cocobank.challenge.portfolio.adapter.out.persistence.repository.MarketPriceProjection;
import com.cocobank.challenge.portfolio.adapter.out.persistence.repository.OrderMovementProjection;
import com.cocobank.challenge.portfolio.adapter.out.persistence.repository.PortfolioReadRepository;
import com.cocobank.challenge.portfolio.application.dto.PortfolioReadData;
import com.cocobank.challenge.portfolio.application.port.out.PortfolioReadPort;
import com.cocobank.challenge.portfolio.domain.MarketPrice;
import com.cocobank.challenge.portfolio.domain.OrderMovement;
import com.cocobank.challenge.portfolio.domain.OrderSide;
import com.cocobank.challenge.portfolio.domain.OrderStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PortfolioReadAdapter implements PortfolioReadPort {

  private static final Logger LOGGER = LoggerFactory.getLogger(PortfolioReadAdapter.class);

  private final PortfolioReadRepository repository;

  public PortfolioReadAdapter(PortfolioReadRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Optional<PortfolioReadData> findByUserIdAndAccountNumber(
      int userId, String accountNumber) {
    if (!accountExists(userId, accountNumber)) {
      return Optional.empty();
    }

    List<OrderMovement> orderMovements = readOrderMovements(userId);
    Map<Integer, MarketPrice> latestPrices = readLatestPrices(orderMovements);
    return Optional.of(new PortfolioReadData(orderMovements, latestPrices));
  }

  private boolean accountExists(int userId, String accountNumber) {
    long accountCheckStarted = System.nanoTime();
    boolean accountExists = repository.existsByUserIdAndAccountNumber(userId, accountNumber);
    LOGGER.debug(
        "portfolio.read.account found={} duration_ms={}",
        accountExists,
        elapsedMillis(accountCheckStarted));
    return accountExists;
  }

  private List<OrderMovement> readOrderMovements(int userId) {
    long movementsStarted = System.nanoTime();
    List<OrderMovementProjection> movementProjections =
        repository.findOrderMovementsByUserId(userId);
    LOGGER.debug(
        "portfolio.read.movements rows={} duration_ms={}",
        movementProjections.size(),
        elapsedMillis(movementsStarted));
    return movementProjections.stream().map(PortfolioReadAdapter::toOrderMovement).toList();
  }

  private Map<Integer, MarketPrice> readLatestPrices(List<OrderMovement> orderMovements) {
    List<Integer> instrumentIds =
        orderMovements.stream()
            .filter(movement ->
                movement.status() == OrderStatus.FILLED
                    && (movement.side() == OrderSide.BUY || movement.side() == OrderSide.SELL))
            .map(OrderMovement::instrumentId)
            .distinct()
            .sorted()
            .toList();
    long pricesStarted = System.nanoTime();
    List<MarketPriceProjection> priceProjections =
        instrumentIds.isEmpty()
            ? List.of()
            : repository.findLatestPricesByInstrumentIds(instrumentIds);
    LOGGER.debug(
        "portfolio.read.market_prices rows={} duration_ms={}",
        priceProjections.size(),
        elapsedMillis(pricesStarted));
    priceProjections.stream()
        .filter(price -> price.getFallbackSource() != null)
        .forEach(price ->
            LOGGER.warn(
                "portfolio.price.fallback instrument_id={} source={} record_date={}",
                price.getInstrumentId(),
                price.getFallbackSource(),
                price.getDate()));
    return priceProjections.stream()
        .collect(
            Collectors.toMap(
                MarketPriceProjection::getInstrumentId,
                price ->
                    new MarketPrice(price.getClose(), price.getDate(), price.getPreviousClose())));
  }

  private static long elapsedMillis(long startedAt) {
    return (System.nanoTime() - startedAt) / 1_000_000;
  }

  private static OrderMovement toOrderMovement(OrderMovementProjection movement) {
    return new OrderMovement(
        movement.getInstrumentId(),
        movement.getSize(),
        movement.getPrice(),
        OrderSide.valueOf(movement.getSide()),
        OrderStatus.valueOf(movement.getStatus()),
        movement.getTicker(),
        movement.getName(),
        movement.getInstrumentType(),
        movement.getDateTime());
  }
}
