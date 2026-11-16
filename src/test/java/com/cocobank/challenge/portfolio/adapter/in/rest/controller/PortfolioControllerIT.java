package com.cocobank.challenge.portfolio.adapter.in.rest.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
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
class PortfolioControllerIT {

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
  void returnsPerformanceForReferencePortfolio() throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(
                URI.create(
                    "http://localhost:" + port + "/api/v1/users/1/portfolio?accountNumber=10001"))
            .GET()
            .build();
    HttpResponse<String> response =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    JsonNode body = new ObjectMapper().readTree(response.body());
    JsonNode instrument = body.path("instruments").get(0);
    assertThat(instrument.path("ticker").asText()).isEqualTo("BMA");
    assertThat(instrument.path("performance").decimalValue()).isEqualByComparingTo("0.23376623");
    assertThat(instrument.has("dailyReturn")).isTrue();
    assertThat(instrument.path("dailyReturn").isNull()).isTrue();
    assertThat(instrument.has("accumulatedReturnPercentage")).isFalse();
  }

  @Test
  void returnsPortfolioWithHistoricalPreviousAndUnavailablePrices() throws Exception {
    int historical = createInstrument("HIST");
    int previous = createInstrument("PREV");
    int unavailable = createInstrument("MISSING");
    jdbcTemplate.update(
        "INSERT INTO orders (instrumentid, userid, size, price, side, status) VALUES"
            + " (66, 2, 1000, 1, 'CASH_IN', 'FILLED')");
    for (int id : new int[] {historical, previous, unavailable}) {
      jdbcTemplate.update(
          "INSERT INTO orders (instrumentid, userid, size, price, side, status)"
              + " VALUES (?, 2, 2, 10, 'BUY', 'FILLED')",
          id);
    }
    jdbcTemplate.update(
        "INSERT INTO marketdata (instrumentid, date, close, previousclose)"
            + " VALUES (?, '2023-07-12', 20, NULL), (?, '2023-07-13', 0, 99),"
            + " (?, '2023-07-13', NULL, 30), (?, '2023-07-13', 0, 0)",
        historical,
        historical,
        previous,
        unavailable);

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(
                URI.create(
                    "http://localhost:" + port + "/api/v1/users/2/portfolio?accountNumber=10002"))
            .GET()
            .build();
    HttpResponse<String> response =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    JsonNode body = new ObjectMapper().readTree(response.body());
    assertThat(body.path("totalAccountValue").decimalValue()).isEqualByComparingTo("1040");
    JsonNode instruments = body.path("instruments");
    assertThat(instruments).hasSize(3);
    assertThat(instruments.get(0).path("marketValue").decimalValue()).isEqualByComparingTo("40");
    assertThat(instruments.get(0).path("performance").decimalValue()).isEqualByComparingTo("100");
    assertThat(instruments.get(1).path("marketValue").decimalValue()).isEqualByComparingTo("60");
    assertThat(instruments.get(1).path("performance").decimalValue()).isEqualByComparingTo("200");
    assertThat(instruments.get(2).path("quantity").asInt()).isEqualTo(2);
    assertThat(instruments.get(2).path("valuationStatus").asText()).isEqualTo("UNAVAILABLE");
    assertThat(instruments.get(2).path("marketValue").isNull()).isTrue();
    assertThat(instruments.get(2).path("accumulatedReturn").isNull()).isTrue();
    assertThat(instruments.get(2).path("performance").isNull()).isTrue();
  }

  @Test
  void returnsDailyAmountOnlyForAnUnchangedPositionWithTodaysClose() throws Exception {
    LocalDate today = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires"));
    int userId =
        jdbcTemplate.queryForObject(
            "INSERT INTO users (accountnumber) VALUES ('DAILY-TEST') RETURNING id", Integer.class);
    int current = createInstrument("TODAY");
    int traded = createInstrument("TRADED");
    int stale = createInstrument("STALE");
    int unknown = createInstrument("UNDATED");
    int fallback = createInstrument("FALLBACK");
    for (int id : new int[] {current, traded, stale, unknown, fallback}) {
      jdbcTemplate.update(
          "INSERT INTO orders (instrumentid, userid, size, price, side, status, datetime)"
              + " VALUES (?, ?, 2, 10, 'BUY', 'FILLED', ?)",
          id,
          userId,
          id == unknown ? null : Timestamp.valueOf(today.minusDays(1).atTime(12, 0)));
      jdbcTemplate.update(
          "INSERT INTO marketdata (instrumentid, date, close, previousclose)"
              + " VALUES (?, ?, 12, 10)",
          id,
          Date.valueOf(id == stale ? today.minusDays(1) : today));
    }
    jdbcTemplate.update(
        "INSERT INTO orders (instrumentid, userid, size, price, side, status, datetime)"
            + " VALUES (?, ?, 1, 10, 'SELL', 'FILLED', ?)",
        traded,
        userId,
        Timestamp.valueOf(today.atTime(12, 0)));
    jdbcTemplate.update(
        "INSERT INTO marketdata (instrumentid, date, close, previousclose) VALUES (?, ?, 0, 10)",
        fallback,
        Date.valueOf(today));

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(
                URI.create(
                    "http://localhost:"
                        + port
                        + "/api/v1/users/"
                        + userId
                        + "/portfolio?accountNumber=DAILY-TEST"))
            .GET()
            .build();
    HttpResponse<String> response =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    JsonNode instruments = new ObjectMapper().readTree(response.body()).path("instruments");
    assertThat(instruments).hasSize(5);
    assertThat(instruments.get(0).path("dailyReturn").decimalValue()).isEqualByComparingTo("4");
    for (int index = 1; index < 5; index++) {
      assertThat(instruments.get(index).path("dailyReturn").isNull()).isTrue();
    }
  }

  @Test
  void documentsDailyReturnInOpenApi() throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/v3/api-docs"))
            .GET()
            .build();
    HttpResponse<String> response =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    JsonNode dailyReturn =
        new ObjectMapper()
            .readTree(response.body())
            .path("components")
            .path("schemas")
            .path("InstrumentResponse")
            .path("properties")
            .path("dailyReturn");
    assertThat(dailyReturn.isMissingNode()).isFalse();
    assertThat(dailyReturn.path("description").asText()).contains("pesos", "aproximación");
    assertThat(dailyReturn.path("type").isArray()).isTrue();
    assertThat(dailyReturn.path("type").toString()).contains("\"number\"", "\"null\"");
  }

  private int createInstrument(String ticker) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO instruments (ticker, name, type) VALUES (?, ?, 'ACCIONES') RETURNING id",
        Integer.class,
        ticker,
        ticker);
  }
}
