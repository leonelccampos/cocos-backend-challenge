package com.cocobank.challenge.portfolio.domain;

import java.math.BigDecimal;

public record PortfolioValuation(BigDecimal marketValue, ValuationStatus status) {}
