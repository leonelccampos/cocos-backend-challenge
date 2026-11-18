package com.cocobank.challenge.adapter.in.rest.representation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record SubmitOrderRequest(
    @NotNull @Positive Integer instrumentId,
    @NotNull String side,
    @NotNull String type,
    Integer size,
    BigDecimal amount,
    BigDecimal price) {}
