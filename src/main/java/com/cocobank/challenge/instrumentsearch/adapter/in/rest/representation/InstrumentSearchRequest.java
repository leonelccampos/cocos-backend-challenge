package com.cocobank.challenge.instrumentsearch.adapter.in.rest.representation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record InstrumentSearchRequest(
    @Schema(description = "Texto no vacío a buscar", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @Pattern(regexp = "(?s).*\\P{javaWhitespace}.*")
        String query,
    @Schema(
            description = "Campo en el que se busca",
            allowableValues = {"ticker", "name"},
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @Pattern(regexp = "ticker|name")
        String field) {}
