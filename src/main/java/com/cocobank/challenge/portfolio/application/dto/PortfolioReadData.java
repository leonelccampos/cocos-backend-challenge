package com.cocobank.challenge.portfolio.application.dto;

import com.cocobank.challenge.portfolio.domain.MarketPrice;
import com.cocobank.challenge.portfolio.domain.OrderMovement;
import java.util.List;
import java.util.Map;

public record PortfolioReadData(
    List<OrderMovement> orderMovements, Map<Integer, MarketPrice> latestPrices) {

  public PortfolioReadData {
    orderMovements = List.copyOf(orderMovements);
    latestPrices = Map.copyOf(latestPrices);
  }
}
