package com.cocobank.challenge.portfolio.adapter.in.rest.representation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PortfolioRequest(@NotBlank @Size(max = 20) String accountNumber) {}
