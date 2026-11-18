package com.cocobank.challenge.adapter.in.rest.controller;

import com.cocobank.challenge.adapter.in.rest.representation.InstrumentSearchRequest;
import com.cocobank.challenge.adapter.in.rest.representation.InstrumentSearchResponse;
import com.cocobank.challenge.application.dto.InstrumentSearchField;
import com.cocobank.challenge.application.usecase.SearchInstrumentsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/instruments")
public class InstrumentSearchController {

  private final SearchInstrumentsUseCase useCase;

  public InstrumentSearchController(SearchInstrumentsUseCase useCase) {
    this.useCase = useCase;
  }

  @GetMapping
  @Operation(summary = "Buscar instrumentos por ticker o nombre")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Instrumentos coincidentes"),
    @ApiResponse(responseCode = "400", description = "Parámetros inválidos"),
    @ApiResponse(responseCode = "404", description = "Sin instrumentos coincidentes"),
    @ApiResponse(responseCode = "500", description = "Error interno")
  })
  public List<InstrumentSearchResponse> search(
      @ParameterObject @Valid @ModelAttribute InstrumentSearchRequest request) {
    InstrumentSearchField field =
        switch (request.field()) {
          case "ticker" -> InstrumentSearchField.TICKER;
          case "name" -> InstrumentSearchField.NAME;
          default -> throw new IllegalArgumentException("Invalid search field");
        };
    return useCase.execute(request.query(), field).stream()
        .map(InstrumentSearchResponse::from)
        .toList();
  }
}
