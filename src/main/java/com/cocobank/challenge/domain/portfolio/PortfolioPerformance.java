package com.cocobank.challenge.domain.portfolio;

import com.cocobank.challenge.domain.order.Order;
import com.cocobank.challenge.domain.order.OrderSide;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public record PortfolioPerformance(
    BigDecimal accumulatedReturn, BigDecimal accumulatedReturnPercentage) {

  private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
  private static final int PERCENTAGE_SCALE = 8;

  public static PortfolioPerformance calculate(List<Order> orders, PortfolioValuation valuation) {
    BigDecimal purchaseCost = BigDecimal.ZERO;
    BigDecimal saleProceeds = BigDecimal.ZERO;
    for (Order order : orders) {
      if (!order.affectsExecutedPortfolio()) {
        continue;
      }
      if (order.side() == OrderSide.BUY) {
        purchaseCost = purchaseCost.add(order.value());
      } else {
        saleProceeds = saleProceeds.add(order.value());
      }
    }

    if (valuation.status() == ValuationStatus.UNAVAILABLE) {
      return new PortfolioPerformance(null, null);
    }

    BigDecimal accumulatedReturn = saleProceeds.add(valuation.marketValue()).subtract(purchaseCost);
    BigDecimal percentage =
        purchaseCost.signum() == 0
            ? null
            : accumulatedReturn
                .multiply(ONE_HUNDRED)
                .divide(purchaseCost, PERCENTAGE_SCALE, RoundingMode.HALF_EVEN);
    return new PortfolioPerformance(accumulatedReturn, percentage);
  }
}
