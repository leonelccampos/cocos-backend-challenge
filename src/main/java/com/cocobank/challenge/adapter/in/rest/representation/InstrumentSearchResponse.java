package com.cocobank.challenge.adapter.in.rest.representation;

import com.cocobank.challenge.application.dto.InstrumentSearchResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

public record InstrumentSearchResponse(
    int id,
    String ticker,
    String name,
    String type,
    @Schema(
            description = "Precio de cierre en pesos, o null si no está disponible",
            nullable = true)
        BigDecimal marketPrice,
    @Schema(description = "Fecha del cierre; null si se usa previousClose", nullable = true)
        LocalDate closeDate) {

  public static InstrumentSearchResponse from(InstrumentSearchResult result) {
    return new InstrumentSearchResponse(
        result.id(),
        result.ticker(),
        result.name(),
        result.type(),
        result.marketPrice(),
        result.closeDate());
  }
}
