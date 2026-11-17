package com.cocobank.challenge.instrumentsearch.adapter.out.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.cocobank.challenge.instrumentsearch.application.usecase.SearchInstrumentsUseCase;
import com.cocobank.challenge.instrumentsearch.domain.InstrumentSearchField;
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

  @Autowired private SearchInstrumentsUseCase useCase;

  @DynamicPropertySource
  static void configureDataSource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Test
  void wiresUseCaseThroughReadAdapterToRealPostgres() {
    Integer id = insertInstrument("WIRING", "Wiring stock", "ACCIONES");
    insertPrice(id, "2024-01-01", "12.50", "10.00");

    assertThat(useCase.execute("wiring", InstrumentSearchField.TICKER))
        .singleElement()
        .satisfies(result -> {
          assertThat(result.id()).isEqualTo(id);
          assertThat(result.marketPrice()).isEqualByComparingTo("12.50");
          assertThat(result.closeDate()).isEqualTo(LocalDate.parse("2024-01-01"));
        });
  }

  @Test
  void projectsEachInstrumentOnceWithLatestPositiveCloseAndLatestPreviousClose() {
    Integer stockId = insertInstrument("TEST", "Test stock", "ACCIONES");
    Integer currencyId = insertInstrument("TSTCUR", "Test currency", "MONEDA");
    insertPrice(stockId, "2024-01-01", "10.00", "9.00");
    insertPrice(stockId, "2024-02-01", "20.00", "19.00");
    insertPrice(stockId, "2024-02-01", "30.00", "29.00");
    insertPrice(stockId, "2024-03-01", "0.00", "40.00");
    insertPrice(stockId, "2024-03-01", "-1.00", "50.00");

    List<InstrumentSearchProjection> results = repository.findAllInstrumentSearchCandidates();

    Integer instrumentCount =
        jdbcTemplate.queryForObject("SELECT count(*) FROM instruments", Integer.class);
    assertThat(results).hasSize(instrumentCount);
    assertThat(results).extracting(InstrumentSearchProjection::getId).doesNotHaveDuplicates();
    assertThat(results)
        .filteredOn(result -> result.getId().equals(stockId))
        .singleElement()
        .satisfies(result -> {
          assertThat(result.getTicker()).isEqualTo("TEST");
          assertThat(result.getName()).isEqualTo("Test stock");
          assertThat(result.getType()).isEqualTo("ACCIONES");
          assertThat(result.getLatestPositiveClose()).isEqualByComparingTo("30.00");
          assertThat(result.getLatestPositiveCloseDate()).isEqualTo(LocalDate.parse("2024-02-01"));
          assertThat(result.getLatestDatedPreviousClose()).isEqualByComparingTo("50.00");
        });
    assertThat(results)
        .filteredOn(result -> result.getId().equals(currencyId))
        .singleElement()
        .satisfies(result -> {
          assertThat(result.getTicker()).isEqualTo("TSTCUR");
          assertThat(result.getName()).isEqualTo("Test currency");
          assertThat(result.getType()).isEqualTo("MONEDA");
          assertThat(result.getLatestPositiveClose()).isNull();
          assertThat(result.getLatestPositiveCloseDate()).isNull();
          assertThat(result.getLatestDatedPreviousClose()).isNull();
        });
  }

  @Test
  void ignoresUndatedAndNonPositiveClosesAndReturnsOnlyLatestDatedPreviousClose() {
    Integer id = insertInstrument("LIMIT", "Test limits", "ACCIONES");
    insertPrice(id, "2024-01-01", "0", "10");
    insertPrice(id, "2024-02-01", "-1", "20");
    insertPrice(id, "2024-02-01", null, "30");
    insertPrice(id, null, "999", "999");

    InstrumentSearchProjection result = projectionFor(id);

    assertThat(result.getLatestPositiveClose()).isNull();
    assertThat(result.getLatestPositiveCloseDate()).isNull();
    assertThat(result.getLatestDatedPreviousClose()).isEqualByComparingTo("30");
  }

  @Test
  void preservesNullsAndDoesNotUseAnOlderPreviousClose() {
    Integer id = insertInstrument(null, null, null);
    insertPrice(id, "2024-01-01", null, "20");
    insertPrice(id, "2024-02-01", null, null);

    InstrumentSearchProjection result = projectionFor(id);

    assertThat(result.getTicker()).isNull();
    assertThat(result.getName()).isNull();
    assertThat(result.getType()).isNull();
    assertThat(result.getLatestPositiveClose()).isNull();
    assertThat(result.getLatestPositiveCloseDate()).isNull();
    assertThat(result.getLatestDatedPreviousClose()).isNull();
  }

  @Test
  void keepsNonPositiveLatestPreviousCloseInsteadOfAnOlderPositiveValue() {
    Integer id = insertInstrument("NOPRICE", "No usable price", "ACCIONES");
    insertPrice(id, "2024-01-01", "0", "20");
    insertPrice(id, "2024-02-01", "-1", "-2");

    InstrumentSearchProjection result = projectionFor(id);

    assertThat(result.getLatestPositiveClose()).isNull();
    assertThat(result.getLatestPositiveCloseDate()).isNull();
    assertThat(result.getLatestDatedPreviousClose()).isEqualByComparingTo("-2");
  }

  @Test
  void readingCandidatesDoesNotModifyInstrumentsOrdersOrMarketData() {
    List<Map<String, Object>> instruments =
        jdbcTemplate.queryForList("SELECT * FROM instruments ORDER BY id");
    List<Map<String, Object>> orders =
        jdbcTemplate.queryForList("SELECT * FROM orders ORDER BY id");
    List<Map<String, Object>> marketData =
        jdbcTemplate.queryForList("SELECT * FROM marketdata ORDER BY id");

    repository.findAllInstrumentSearchCandidates();

    assertThat(jdbcTemplate.queryForList("SELECT * FROM instruments ORDER BY id"))
        .isEqualTo(instruments);
    assertThat(jdbcTemplate.queryForList("SELECT * FROM orders ORDER BY id")).isEqualTo(orders);
    assertThat(jdbcTemplate.queryForList("SELECT * FROM marketdata ORDER BY id"))
        .isEqualTo(marketData);
  }

  private InstrumentSearchProjection projectionFor(Integer id) {
    return repository.findAllInstrumentSearchCandidates().stream()
        .filter(result -> result.getId().equals(id))
        .findFirst()
        .orElseThrow();
  }

  private Integer insertInstrument(String ticker, String name, String type) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO instruments (ticker, name, type) VALUES (?, ?, ?) RETURNING id",
        Integer.class,
        ticker,
        name,
        type);
  }

  private void insertPrice(Integer instrumentId, String date, String close, String previousClose) {
    jdbcTemplate.update(
        "INSERT INTO marketdata (instrumentid, date, close, previousclose) VALUES (?, ?, ?, ?)",
        instrumentId,
        date == null ? null : Date.valueOf(date),
        close == null ? null : new BigDecimal(close),
        previousClose == null ? null : new BigDecimal(previousClose));
  }
}
