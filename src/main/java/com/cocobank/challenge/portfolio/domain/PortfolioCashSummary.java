package com.cocobank.challenge.portfolio.domain;

import java.math.BigDecimal;

public record PortfolioCashSummary(BigDecimal available, BigDecimal reserved) {}
