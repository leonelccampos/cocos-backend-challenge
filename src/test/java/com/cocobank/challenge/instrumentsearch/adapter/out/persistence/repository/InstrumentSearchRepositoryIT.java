package com.cocobank.challenge.instrumentsearch.adapter.out.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("integration")
@SpringBootTest
@Testcontainers
@Transactional
class InstrumentSearchRepositoryIT {

  @Container
  private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

  @Autowired private InstrumentSearchRepository repository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @DynamicPropertySource
  static void configureDataSource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Test
  void searchesSelectedFieldIgnoringLatinAccentsAndCaseAndExcludesOtherTypes() {
    int first = insertInstrument("T-Á12", "Camio\u0301n", "ACCIONES");
    int second = insertInstrument("T-B12", "CAMIÓN", "ACCIONES");
    insertInstrument("T-C12", "Camion", "MONEDA");
    insertInstrument("CAMION", null, "ACCIONES");

    assertThat(repository.search("camión", "NAME"))
        .extracting(InstrumentSearchProjection::getId)
        .containsExactly(second, first);
    assertThat(repository.search("t-a12", "TICKER"))
        .extracting(InstrumentSearchProjection::getId)
        .containsExactly(first);
  }

  @Test
  void matchesQueryLiterallyAndOrdersByTickerThenIdWithNullsLast() {
    int first = insertInstrument("T-AAA", "Find %_", "ACCIONES");
    int second = insertInstrument("T-AAA", "Find %_", "ACCIONES");
    int third = insertInstrument(null, "Find %_", "ACCIONES");
    insertInstrument("T-BBB", "Find text", "ACCIONES");

    assertThat(repository.search("%_", "NAME"))
        .extracting(InstrumentSearchProjection::getId)
        .containsExactly(first, second, third);
  }

  @Test
  void choosesLatestPositiveDatedCloseWithIdTiebreaker() {
    int id = insertInstrument("PRICE", "Price", "ACCIONES");
    insertPrice(id, "2024-01-01", "10", "9");
    insertPrice(id, "2024-02-01", "20", "19");
    insertPrice(id, "2024-02-01", "30", "29");
    insertPrice(id, "2024-03-01", "0", "50");
    insertPrice(id, null, "999", "999");

    assertThat(repository.search("PRICE", "TICKER"))
        .singleElement()
        .satisfies(result -> {
          assertThat(result.getMarketPrice()).isEqualByComparingTo("30");
          assertThat(result.getCloseDate()).isEqualTo(LocalDate.parse("2024-02-01"));
        });
  }

  @Test
  void usesOnlyPositivePreviousCloseOfLatestDatedRowWithoutDate() {
    int fallback = insertInstrument("FALLBACK1", "Fallback", "ACCIONES");
    int unavailable = insertInstrument("FALLBACK2", "Fallback", "ACCIONES");
    insertPrice(fallback, "2024-01-01", "0", "10");
    insertPrice(fallback, "2024-02-01", "-1", "20");
    insertPrice(fallback, "2024-02-01", null, "30");
    insertPrice(fallback, null, "999", "999");
    insertPrice(unavailable, "2024-01-01", "0", "10");
    insertPrice(unavailable, "2024-02-01", null, "-2");

    assertThat(repository.search("FALLBACK", "TICKER"))
        .satisfiesExactly(
            result -> {
              assertThat(result.getId()).isEqualTo(fallback);
              assertThat(result.getMarketPrice()).isEqualByComparingTo("30");
              assertThat(result.getCloseDate()).isNull();
            },
            result -> {
              assertThat(result.getId()).isEqualTo(unavailable);
              assertThat(result.getMarketPrice()).isNull();
              assertThat(result.getCloseDate()).isNull();
            });
  }

  @Test
  void searchDoesNotModifyInstrumentsOrdersOrMarketData() {
    List<Map<String, Object>> instruments =
        jdbcTemplate.queryForList("SELECT * FROM instruments ORDER BY id");
    List<Map<String, Object>> orders =
        jdbcTemplate.queryForList("SELECT * FROM orders ORDER BY id");
    List<Map<String, Object>> marketData =
        jdbcTemplate.queryForList("SELECT * FROM marketdata ORDER BY id");

    repository.search("GGAL", "TICKER");

    assertThat(jdbcTemplate.queryForList("SELECT * FROM instruments ORDER BY id"))
        .isEqualTo(instruments);
    assertThat(jdbcTemplate.queryForList("SELECT * FROM orders ORDER BY id")).isEqualTo(orders);
    assertThat(jdbcTemplate.queryForList("SELECT * FROM marketdata ORDER BY id"))
        .isEqualTo(marketData);
  }

  private int insertInstrument(String ticker, String name, String type) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO instruments (ticker, name, type) VALUES (?, ?, ?) RETURNING id",
        Integer.class,
        ticker,
        name,
        type);
  }

  private void insertPrice(int instrumentId, String date, String close, String previousClose) {
    jdbcTemplate.update(
        "INSERT INTO marketdata (instrumentid, date, close, previousclose) VALUES (?, ?, ?, ?)",
        instrumentId,
        date == null ? null : Date.valueOf(date),
        close == null ? null : new BigDecimal(close),
        previousClose == null ? null : new BigDecimal(previousClose));
  }
}
