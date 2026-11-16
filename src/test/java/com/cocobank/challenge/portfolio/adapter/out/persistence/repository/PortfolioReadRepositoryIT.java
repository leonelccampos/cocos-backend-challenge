package com.cocobank.challenge.portfolio.adapter.out.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
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
class PortfolioReadRepositoryIT {

  @Container
  private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

  @Autowired private PortfolioReadRepository repository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @DynamicPropertySource
  static void configureDataSource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Test
  void returnsFilledMovementsAndPendingBuysOnly() {
    List<OrderMovementProjection> movements = repository.findOrderMovementsByUserId(1);

    assertThat(movements).hasSize(9);
    assertThat(movements)
        .extracting(OrderMovementProjection::getStatus)
        .containsOnly("FILLED", "NEW");
    assertThat(movements)
        .filteredOn(movement -> "NEW".equals(movement.getStatus()))
        .extracting(OrderMovementProjection::getSide)
        .containsOnly("BUY", "BUY");
    assertThat(movements)
        .filteredOn(movement -> "CASH_IN".equals(movement.getSide()))
        .singleElement()
        .satisfies(movement -> {
          assertThat(movement.getSize()).isEqualTo(1_000_000);
          assertThat(movement.getTicker()).isEqualTo("ARS");
          assertThat(movement.getInstrumentType()).isEqualTo("MONEDA");
          assertThat(movement.getDateTime()).isEqualTo(LocalDateTime.parse("2023-07-12T12:11:20"));
        });
    assertThat(repository.findOrderMovementsByUserId(2)).isEmpty();
  }

  @Test
  void checksUserIdAndAccountNumberTogether() {
    assertThat(repository.existsByUserIdAndAccountNumber(1, "10001")).isTrue();
    assertThat(repository.existsByUserIdAndAccountNumber(1, "10002")).isFalse();
    assertThat(repository.existsByUserIdAndAccountNumber(999, "10001")).isFalse();
  }

  @Test
  void selectsMostRecentPriceAndBreaksSameDateTiesByGreaterId() {
    jdbcTemplate.update(
        "INSERT INTO marketdata (instrumentid, \"date\", \"close\") VALUES (?, ?, ?)",
        47,
        java.sql.Date.valueOf("2023-07-15"),
        new BigDecimal("999.00"));
    jdbcTemplate.update(
        "INSERT INTO marketdata (instrumentid, \"date\", \"close\") VALUES (?, ?, ?)",
        47,
        java.sql.Date.valueOf("2023-07-15"),
        new BigDecimal("1000.00"));

    List<MarketPriceProjection> prices = repository.findLatestPricesByInstrumentIds(List.of(47));

    assertThat(prices)
        .singleElement()
        .satisfies(price -> {
          assertThat(price.getClose()).isEqualByComparingTo("1000.00");
          assertThat(price.getDate()).isEqualTo(java.time.LocalDate.parse("2023-07-15"));
        });
  }

  @Test
  void returnsNoPriceForInstrumentWithoutMarketData() {
    assertThat(repository.findLatestPricesByInstrumentIds(List.of(66))).isEmpty();
  }

  @Test
  void selectsMostRecentPositiveCloseBeforeTryingPreviousClose() {
    insertPrice("2023-07-12", "10", null);
    insertPrice("2023-07-13", "20", null);
    insertPrice("2023-07-13", "30", null);
    insertPrice("2023-07-13", "0", "999");
    insertPrice("2023-07-14", "-1", "999");
    insertPrice(null, "1000", "1000");

    assertThat(repository.findLatestPricesByInstrumentIds(List.of(66)))
        .singleElement()
        .satisfies(price -> {
          assertThat(price.getClose()).isEqualByComparingTo("30");
          assertThat(price.getDate()).isEqualTo(java.time.LocalDate.parse("2023-07-13"));
          assertThat(price.getFallbackSource()).isEqualTo("HISTORICAL_CLOSE");
          assertThat(price.getPreviousClose()).isNull();
        });
  }

  @Test
  void usesPreviousCloseOfLatestDatedRecordOnlyWhenNoPositiveCloseExists() {
    insertPrice("2023-07-12", "0", "10");
    insertPrice("2023-07-13", "-1", "20");
    insertPrice("2023-07-13", null, "30");
    insertPrice(null, "1000", "1000");

    assertThat(repository.findLatestPricesByInstrumentIds(List.of(66)))
        .singleElement()
        .satisfies(price -> {
          assertThat(price.getClose()).isEqualByComparingTo("30");
          assertThat(price.getDate()).isEqualTo(java.time.LocalDate.parse("2023-07-13"));
          assertThat(price.getFallbackSource()).isEqualTo("PREVIOUS_CLOSE");
          assertThat(price.getPreviousClose()).isNull();
        });
  }

  @Test
  void returnsPreviousCloseOfTheSelectedLatestClose() {
    insertPrice("2023-07-12", "10", "9");
    insertPrice("2023-07-13", "20", "15");

    assertThat(repository.findLatestPricesByInstrumentIds(List.of(66)))
        .singleElement()
        .satisfies(price -> {
          assertThat(price.getClose()).isEqualByComparingTo("20");
          assertThat(price.getPreviousClose()).isEqualByComparingTo("15");
          assertThat(price.getFallbackSource()).isNull();
        });
  }

  @Test
  void doesNotUseAnOlderPreviousCloseWhenTheLatestIsInvalid() {
    insertPrice("2023-07-12", "0", "10");
    insertPrice("2023-07-13", "-1", "0");

    assertThat(repository.findLatestPricesByInstrumentIds(List.of(66)))
        .singleElement()
        .satisfies(price -> {
          assertThat(price.getClose()).isEqualByComparingTo("-1");
          assertThat(price.getFallbackSource()).isNull();
        });
  }

  private void insertPrice(String date, String close, String previousClose) {
    jdbcTemplate.update(
        "INSERT INTO marketdata (instrumentid, \"date\", \"close\", previousclose)"
            + " VALUES (?, ?, ?, ?)",
        66,
        date == null ? null : java.sql.Date.valueOf(date),
        close == null ? null : new BigDecimal(close),
        previousClose == null ? null : new BigDecimal(previousClose));
  }

  @BeforeEach
  void addNewPendingSellThatMustNotBeRead() {
    jdbcTemplate.update(
        """
        INSERT INTO orders (instrumentid, userid, size, price, type, side, status, datetime)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        47,
        1,
        1,
        930.00,
        "LIMIT",
        "SELL",
        "NEW",
        Timestamp.valueOf(LocalDateTime.parse("2023-07-15T10:00:00")));
  }
}
