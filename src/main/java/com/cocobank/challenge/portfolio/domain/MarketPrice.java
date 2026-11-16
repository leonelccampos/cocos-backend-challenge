package com.cocobank.challenge.portfolio.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MarketPrice(BigDecimal close, LocalDate date, BigDecimal previousClose) {}
