package com.cocobank.challenge.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InstrumentSearchResult(
    int id, String ticker, String name, String type, BigDecimal marketPrice, LocalDate closeDate) {}
