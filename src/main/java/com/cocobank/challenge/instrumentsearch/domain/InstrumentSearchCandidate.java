package com.cocobank.challenge.instrumentsearch.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InstrumentSearchCandidate(
    int id,
    String ticker,
    String name,
    String type,
    BigDecimal latestPositiveClose,
    LocalDate latestPositiveCloseDate,
    BigDecimal latestDatedPreviousClose) {}
