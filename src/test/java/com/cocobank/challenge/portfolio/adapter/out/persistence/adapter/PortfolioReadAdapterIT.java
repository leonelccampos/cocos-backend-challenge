package com.cocobank.challenge.portfolio.adapter.out.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.cocobank.challenge.portfolio.application.dto.PortfolioReadData;
import com.cocobank.challenge.portfolio.application.usecase.GetPortfolioUseCase;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("integration")
@SpringBootTest
@Testcontainers
class PortfolioReadAdapterIT {

  @Container
  private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

  @Autowired private PortfolioReadAdapter adapter;

  @Autowired private GetPortfolioUseCase getPortfolioUseCase;

  @Autowired private JdbcTemplate jdbcTemplate;

  @DynamicPropertySource
  static void configureDataSource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Test
  void mapsReadModelsForTheMatchingAccount() {
    Optional<PortfolioReadData> result = adapter.findByUserIdAndAccountNumber(1, "10001");

    assertThat(result)
        .isPresent()
        .get()
        .satisfies(data -> {
          assertThat(data.orderMovements()).hasSize(9);
          assertThat(data.latestPrices().keySet()).containsExactlyInAnyOrder(31, 47, 54);
          assertThat(data.latestPrices().get(47).close()).isNotNull();
          assertThat(data.latestPrices().get(47).date()).isEqualTo(LocalDate.parse("2023-07-14"));
        });
  }

  @Test
  void calculatesReferenceAccountWithNegativePosition() {
    var portfolio = getPortfolioUseCase.execute(1, "10001");

    assertThat(portfolio.availableCash()).isEqualByComparingTo("627500");
    assertThat(portfolio.reservedCash()).isEqualByComparingTo("125500");
    assertThat(portfolio.instruments())
        .filteredOn(instrument -> instrument.instrumentId() == 31)
        .singleElement()
        .satisfies(instrument -> {
          assertThat(instrument.quantity()).isEqualTo(-10);
          assertThat(instrument.marketValue()).isEqualByComparingTo("-15028");
          assertThat(instrument.accumulatedReturn()).isEqualByComparingTo("72");
          assertThat(instrument.accumulatedReturnPercentage()).isEqualByComparingTo("0.23376623");
        });
  }

  @Test
  void readsOnlyTheRequestedUsersFilledAndPendingBuyMovements() {
    jdbcTemplate.update(
        """
        INSERT INTO orders (instrumentid, userid, size, price, type, side, status, datetime)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        66,
        3,
        12_345,
        1,
        "MARKET",
        "CASH_IN",
        "FILLED",
        Timestamp.valueOf(LocalDateTime.parse("2023-07-15T10:00:00")));

    PortfolioReadData firstUser = adapter.findByUserIdAndAccountNumber(1, "10001").orElseThrow();
    PortfolioReadData thirdUser = adapter.findByUserIdAndAccountNumber(3, "10003").orElseThrow();

    assertThat(firstUser.orderMovements()).hasSize(9);
    assertThat(firstUser.orderMovements())
        .extracting(movement -> movement.status().name())
        .containsOnly("FILLED", "NEW");
    assertThat(firstUser.orderMovements())
        .filteredOn(movement -> movement.status().name().equals("NEW"))
        .extracting(movement -> movement.side().name())
        .containsOnly("BUY", "BUY");
    assertThat(firstUser.orderMovements()).noneMatch(movement -> movement.size() == 12_345);
    assertThat(thirdUser.orderMovements())
        .singleElement()
        .satisfies(movement -> {
          assertThat(movement.side().name()).isEqualTo("CASH_IN");
          assertThat(movement.size()).isEqualTo(12_345);
        });
  }

  @Test
  void mapsThePriceWithTheGreaterIdForTheLatestDate() {
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

    PortfolioReadData result = adapter.findByUserIdAndAccountNumber(1, "10001").orElseThrow();

    assertThat(result.latestPrices().get(47).close()).isEqualByComparingTo("1000.00");
    assertThat(result.latestPrices().get(47).date()).isEqualTo(LocalDate.parse("2023-07-15"));
  }

  @Test
  void returnsEmptySnapshotForExistingAccountWithoutActivity() {
    Optional<PortfolioReadData> result = adapter.findByUserIdAndAccountNumber(2, "10002");

    assertThat(result)
        .isPresent()
        .get()
        .satisfies(data -> {
          assertThat(data.orderMovements()).isEmpty();
          assertThat(data.latestPrices()).isEmpty();
        });
  }

  @Test
  void returnsAbsentForAnUnmatchedAccount() {
    assertThat(adapter.findByUserIdAndAccountNumber(1, "10002")).isEmpty();
    assertThat(adapter.findByUserIdAndAccountNumber(999, "10001")).isEmpty();
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
