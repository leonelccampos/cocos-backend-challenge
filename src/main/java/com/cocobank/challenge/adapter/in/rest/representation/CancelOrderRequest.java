package com.cocobank.challenge.adapter.in.rest.representation;

import jakarta.validation.constraints.NotBlank;

public record CancelOrderRequest(@NotBlank String status) {}
