package com.cocobank.challenge.instrumentsearch.adapter.in.rest.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class InstrumentSearchControllerIT {

  @Container
  private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

  @LocalServerPort private int port;
  @Autowired private JdbcTemplate jdbcTemplate;

  @DynamicPropertySource
  static void configureDataSource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Test
  void openApiDescribesParametersAndResponses() throws Exception {
    HttpResponse<String> response = get("/v3/api-docs");
    assertThat(response.statusCode()).isEqualTo(200);
    JsonNode operation =
        new ObjectMapper()
            .readTree(response.body())
            .path("paths")
            .path("/api/v1/instruments")
            .path("get");
    assertThat(operation.path("summary").asText()).isNotBlank();
    assertThat(operation.path("responses").has("200")).isTrue();
    assertThat(operation.path("responses").has("400")).isTrue();
    assertThat(operation.path("responses").has("404")).isTrue();
    assertThat(operation.path("parameters")).hasSize(2);
    JsonNode query = parameter(operation, "query");
    JsonNode field = parameter(operation, "field");
    assertThat(query.path("required").asBoolean()).isTrue();
    assertThat(field.path("required").asBoolean()).isTrue();
    assertThat(field.path("schema").path("enum").toString()).contains("\"ticker\"", "\"name\"");
  }

  @Test
  void searchesOverHttpWithoutChangingCatalogOrdersOrPrices() throws Exception {
    int first = insertInstrument("AT12", "Árbol uno", "ACCIONES");
    int second = insertInstrument("AT12", "Arbol dos", "ACCIONES");
    int third = insertInstrument("ZT12", "ÁRBOL tres", "ACCIONES");
    insertInstrument("BT12", "Arbol moneda", "MONEDA");
    jdbcTemplate.update(
        """
        INSERT INTO marketdata (instrumentid, date, close, previousclose)
        VALUES (?, '2024-01-01', 15, 11), (?, '2024-01-02', 0, 20)
        """,
        first,
        second);
    List<Map<String, Object>> instruments =
        jdbcTemplate.queryForList("SELECT * FROM instruments ORDER BY id");
    List<Map<String, Object>> orders =
        jdbcTemplate.queryForList("SELECT * FROM orders ORDER BY id");
    List<Map<String, Object>> prices =
        jdbcTemplate.queryForList("SELECT * FROM marketdata ORDER BY id");

    HttpResponse<String> byName = get("/api/v1/instruments?query=arbol&field=name");
    assertThat(byName.statusCode()).isEqualTo(200);
    JsonNode results = new ObjectMapper().readTree(byName.body());
    assertThat(results).hasSize(3);
    assertThat(results.get(0).path("id").asInt()).isEqualTo(first);
    assertThat(results.get(1).path("id").asInt()).isEqualTo(second);
    assertThat(results.get(2).path("id").asInt()).isEqualTo(third);
    assertThat(results.get(0).path("marketPrice").decimalValue()).isEqualByComparingTo("15");
    assertThat(results.get(0).path("closeDate").asText()).isEqualTo("2024-01-01");
    assertThat(results.get(1).path("marketPrice").decimalValue()).isEqualByComparingTo("20");
    assertThat(results.get(1).has("closeDate")).isTrue();
    assertThat(results.get(1).path("closeDate").isNull()).isTrue();
    assertThat(results.get(2).has("marketPrice")).isTrue();
    assertThat(results.get(2).path("marketPrice").isNull()).isTrue();
    assertThat(results.get(2).path("closeDate").isNull()).isTrue();

    HttpResponse<String> byTicker = get("/api/v1/instruments?query=t12&field=ticker");
    assertThat(byTicker.statusCode()).isEqualTo(200);
    assertThat(new ObjectMapper().readTree(byTicker.body())).hasSize(3);
    assertThat(get("/api/v1/instruments?query=%20%20&field=ticker").statusCode()).isEqualTo(400);
    assertThat(get("/api/v1/instruments?query=abc&field=wrong").statusCode()).isEqualTo(400);
    assertThat(get("/api/v1/instruments?query=NO_MATCH_T12&field=ticker").statusCode())
        .isEqualTo(404);
    assertThat(jdbcTemplate.queryForList("SELECT * FROM instruments ORDER BY id"))
        .isEqualTo(instruments);
    assertThat(jdbcTemplate.queryForList("SELECT * FROM orders ORDER BY id")).isEqualTo(orders);
    assertThat(jdbcTemplate.queryForList("SELECT * FROM marketdata ORDER BY id")).isEqualTo(prices);
  }

  private int insertInstrument(String ticker, String name, String type) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO instruments (ticker, name, type) VALUES (?, ?, ?) RETURNING id",
        Integer.class,
        ticker,
        name,
        type);
  }

  private JsonNode parameter(JsonNode operation, String name) {
    for (JsonNode parameter : operation.path("parameters")) {
      if (name.equals(parameter.path("name").asText())) {
        return parameter;
      }
    }
    throw new AssertionError("Missing OpenAPI parameter: " + name);
  }

  private HttpResponse<String> get(String path) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder().uri(URI.create("http://localhost:" + port + path)).GET().build();
    return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
  }
}
