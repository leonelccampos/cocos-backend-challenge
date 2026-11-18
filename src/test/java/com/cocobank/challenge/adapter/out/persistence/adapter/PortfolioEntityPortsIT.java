package com.cocobank.challenge.adapter.out.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cocobank.challenge.application.exception.PortfolioNotFoundException;
import com.cocobank.challenge.application.port.out.InstrumentPort;
import com.cocobank.challenge.application.port.out.MarketDataPort;
import com.cocobank.challenge.application.port.out.OrderPort;
import com.cocobank.challenge.application.usecase.GetPortfolioUseCase;
import com.cocobank.challenge.domain.market.MarketData;
import com.cocobank.challenge.domain.market.MarketDataSelection;
import com.cocobank.challenge.domain.order.InvalidOrderDataException;
import com.cocobank.challenge.domain.portfolio.Portfolio;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
class PortfolioEntityPortsIT {

  @Container
  private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

  @Autowired private GetPortfolioUseCase useCase;
  @Autowired private OrderPort orderPort;
  @Autowired private InstrumentPort instrumentPort;
  @Autowired private MarketDataPort marketDataPort;
  @Autowired private JdbcTemplate jdbcTemplate;

  @DynamicPropertySource
  static void configureDataSource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Test
  void readsEntityDataAndCalculatesReferenceAccount() {
    Portfolio portfolio = useCase.execute(1, "10001");
    assertThat(portfolio.availableCash()).isEqualByComparingTo("627500");
    assertThat(portfolio.reservedCash()).isEqualByComparingTo("125500");
    assertThat(portfolio.instruments())
        .filteredOn(instrument -> instrument.instrumentId() == 31)
        .singleElement()
        .satisfies(instrument -> {
          assertThat(instrument.quantity()).isEqualTo(-10);
          assertThat(instrument.marketValue()).isEqualByComparingTo("-15028");
          assertThat(instrument.accumulatedReturn()).isEqualByComparingTo("72");
        });
    assertThat(instrumentPort.findByIds(Set.of(31, 47)))
        .extracting(instrument -> instrument.id())
        .containsExactlyInAnyOrder(31, 47);
    assertThat(orderPort.findByUserId(2)).isEmpty();
    assertThat(useCase.execute(2, "10002").instruments()).isEmpty();
    assertThatThrownBy(() -> useCase.execute(1, "10002"))
        .isInstanceOf(PortfolioNotFoundException.class);
  }

  @Test
  void readsDatedRealRecordsAndSelectionResolvesTiesAndFallbacks() {
    insertPrice("2023-07-12", "10", null);
    insertPrice("2023-07-13", "20", null);
    insertPrice("2023-07-13", "30", null);
    insertPrice("2023-07-14", "-1", "999");
    insertPrice(null, "1000", "1000");

    List<MarketData> data = marketDataPort.findByInstrumentIds(Set.of(66));
    assertThat(data).hasSize(4).allMatch(record -> record.date() != null);
    MarketDataSelection selection = MarketDataSelection.select(data).orElseThrow();
    assertThat(selection.close()).isEqualByComparingTo("30");
    assertThat(selection.selectedRecord().date()).isEqualTo(LocalDate.parse("2023-07-13"));
    assertThat(selection.usesHistoricalClose()).isTrue();
    assertThat(marketDataPort.findByInstrumentIds(Set.of())).isEmpty();
  }

  @Test
  void rejectsPersistedOrderWithoutInstrumentExplicitly() {
    String accountNumber = UUID.randomUUID().toString().substring(0, 20);
    int userId =
        jdbcTemplate.queryForObject(
            "INSERT INTO users (accountnumber) VALUES (?) RETURNING id",
            Integer.class,
            accountNumber);
    jdbcTemplate.update(
        "INSERT INTO orders (userid, size, price, type, side, status)"
            + " VALUES (?, 1, 10, 'MARKET', 'BUY', 'FILLED')",
        userId);
    assertThatThrownBy(() -> useCase.execute(userId, accountNumber))
        .hasRootCauseInstanceOf(InvalidOrderDataException.class)
        .hasRootCauseMessage("Order is missing an identifier");
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
}
