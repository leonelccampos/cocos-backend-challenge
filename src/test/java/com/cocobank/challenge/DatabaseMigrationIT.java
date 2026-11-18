package com.cocobank.challenge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
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
class DatabaseMigrationIT {

  @Container
  private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

  @Autowired private JdbcTemplate jdbcTemplate;

  @DynamicPropertySource
  static void configureDataSource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Test
  void appliesSchemaAndReferenceData() {
    assertThat(countRows("users")).isEqualTo(4);
    assertThat(countRows("instruments")).isEqualTo(66);
    assertThat(countRows("orders")).isEqualTo(11);
  }

  @Test
  void createsIndexesForPortfolioReadQueries() {
    assertThat(indexDefinition("orders_userid_idx"))
        .contains("on public.orders using btree (userid)");
    assertThat(indexDefinition("orders_instrumentid_idx"))
        .contains("on public.orders using btree (instrumentid)");
    assertThat(indexDefinition("marketdata_instrumentid_date_id_idx"))
        .contains("on public.marketdata using btree (instrumentid, date desc, id desc)")
        .contains("where (date is not null)");
  }

  @Test
  void createsIdempotencyTableForOrderResults() {
    Integer keyColumns =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM information_schema.key_column_usage
            WHERE table_name = 'order_idempotency'
              AND constraint_name = 'order_idempotency_pkey'
              AND column_name IN ('userid', 'idempotencykey')
            """,
            Integer.class);

    assertThat(keyColumns).isEqualTo(2);
    assertThat(countRows("order_idempotency")).isZero();
  }

  private int countRows(String tableName) {
    return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
  }

  private String indexDefinition(String indexName) {
    return jdbcTemplate
        .queryForObject(
            """
            SELECT indexdef
            FROM pg_indexes
            WHERE schemaname = current_schema()
              AND indexname = ?
            """,
            String.class,
            indexName)
        .toLowerCase(Locale.ROOT);
  }
}
