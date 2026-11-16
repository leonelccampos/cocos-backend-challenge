package com.cocobank.challenge.portfolio.domain;

import java.math.BigDecimal;

public record PortfolioPerformance(
    BigDecimal accumulatedReturn, BigDecimal accumulatedReturnPercentage) {}
