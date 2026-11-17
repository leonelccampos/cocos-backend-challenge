package com.cocobank.challenge.instrumentsearch.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InstrumentSearchResult(
    int id, String ticker, String name, String type, BigDecimal marketPrice, LocalDate closeDate) {}
