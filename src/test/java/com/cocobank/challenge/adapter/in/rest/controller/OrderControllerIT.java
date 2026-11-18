package com.cocobank.challenge.adapter.in.rest.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.cocobank.challenge.application.port.out.OrderPort;
import com.cocobank.challenge.domain.order.OrderStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
class OrderControllerIT {

  @Container
  private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

  @LocalServerPort private int port;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private OrderPort orderPort;

  private final HttpClient client = HttpClient.newHttpClient();
  private final ObjectMapper mapper = new ObjectMapper();

  @DynamicPropertySource
  static void configureDataSource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Test
  void createsOnceReplaysOriginalAndConflictsWithDifferentData() throws Exception {
    int instrumentId = createInstrument("ACCIONES", "ORD-RPL");
    int userId = createUserWithCash(25);
    String body = orderBody(instrumentId, "BUY", "LIMIT", "\"amount\":25.00", "\"price\":10.00");

    HttpResponse<String> created = submit(userId, "replay-key", body);
    HttpResponse<String> replay = submit(userId, "replay-key", body);
    HttpResponse<String> conflict =
        submit(
            userId,
            "replay-key",
            orderBody(instrumentId, "BUY", "LIMIT", "\"amount\":20.00", "\"price\":10.00"));

    assertThat(created.statusCode()).isEqualTo(201);
    assertThat(replay.statusCode()).isEqualTo(201);
    assertThat(replay.body()).isEqualTo(created.body());
    assertThat(conflict.statusCode()).isEqualTo(409);
    assertThat(mapper.readTree(created.body()).path("size").asInt()).isEqualTo(2);
    assertThat(countOrders(userId)).isEqualTo(1 + 1);
    assertThat(countIdempotent(userId, "replay-key")).isOne();
  }

  @Test
  void persistsRejectedMarketOrderAndReplaysIts422WithoutRecalculation() throws Exception {
    int userId = createUserWithCash(50);
    int instrumentId = createInstrument("ACCIONES", "ORD-RJC");
    String body = orderBody(instrumentId, "BUY", "MARKET", "\"amount\":75.00", null);
    jdbcTemplate.update(
        "INSERT INTO marketdata (instrumentid, date, close) VALUES (?, CURRENT_DATE, 10)",
        instrumentId);

    HttpResponse<String> rejected = submit(userId, "reject-key", body);
    jdbcTemplate.update("UPDATE marketdata SET close = 1 WHERE instrumentid = ?", instrumentId);
    HttpResponse<String> replay = submit(userId, "reject-key", body);

    assertThat(rejected.statusCode()).isEqualTo(422);
    assertThat(replay.statusCode()).isEqualTo(422);
    JsonNode problem = mapper.readTree(replay.body());
    assertThat(problem.path("reason").asText()).isEqualTo("INSUFFICIENT_CASH");
    assertThat(problem.at("/order/status").asText()).isEqualTo("REJECTED");
    assertThat(problem.at("/order/size").asInt()).isEqualTo(7);
    assertThat(countOrders(userId)).isEqualTo(2);
  }

  @Test
  void rejectsMarketAmountWithoutCloseWithNullPriceAndSize() throws Exception {
    int userId = createUserWithCash(1_000);
    int instrumentId = createInstrument("ACCIONES", "NO-PRC");

    HttpResponse<String> response =
        submit(
            userId,
            "no-close-key",
            orderBody(instrumentId, "BUY", "MARKET", "\"amount\":100.00", null));

    assertThat(response.statusCode()).isEqualTo(422);
    assertThat(mapper.readTree(response.body()).at("/order/size").isNull()).isTrue();
    assertThat(mapper.readTree(response.body()).at("/order/price").isNull()).isTrue();
  }

  @Test
  void readsAllUserOrdersAndIgnoresRejectedAndCancelledOrdersInAvailability() throws Exception {
    int userId = createUserWithCash(100);
    int otherUserId = createUserWithCash(200);
    int instrumentId = createInstrument("ACCIONES", "ORD-ALL");
    jdbcTemplate.update("UPDATE orders SET type = NULL WHERE userid = ?", userId);
    jdbcTemplate.update(
        "INSERT INTO orders (instrumentid, userid, type, side, status)"
            + " VALUES (?, ?, 'LIMIT', 'BUY', 'CANCELLED')",
        instrumentId,
        userId);

    HttpResponse<String> rejected =
        submit(
            userId,
            "missing-close-all",
            orderBody(instrumentId, "BUY", "MARKET", "\"amount\":10", null));
    HttpResponse<String> accepted =
        submit(
            userId,
            "after-rejected-all",
            orderBody(instrumentId, "BUY", "LIMIT", "\"size\":1", "\"price\":100"));

    assertThat(rejected.statusCode()).isEqualTo(422);
    assertThat(accepted.statusCode()).isEqualTo(201);
    assertThat(orderPort.findByUserId(userId))
        .extracting(order -> order.status())
        .containsExactly(
            OrderStatus.FILLED, OrderStatus.CANCELLED, OrderStatus.REJECTED, OrderStatus.NEW);
    assertThat(orderPort.findByUserId(userId).get(0).type()).isNull();
    assertThat(orderPort.findByUserId(otherUserId)).hasSize(1);
  }

  @Test
  void enforcesAvailabilityUnderConcurrentRequestsForTheSameUser() throws Exception {
    int userId = createUserWithCash(100);
    int instrumentId = createInstrument("ACCIONES", "ORD-CNC");
    String body = orderBody(instrumentId, "BUY", "LIMIT", "\"size\":1", "\"price\":100.00");

    CompletableFuture<HttpResponse<String>> first =
        CompletableFuture.supplyAsync(() -> uncheckedSubmit(userId, "concurrent-a", body));
    CompletableFuture<HttpResponse<String>> second =
        CompletableFuture.supplyAsync(() -> uncheckedSubmit(userId, "concurrent-b", body));
    List<Integer> statuses =
        List.of(first.join().statusCode(), second.join().statusCode()).stream().sorted().toList();

    assertThat(statuses).containsExactly(201, 422);
    int accepted =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM orders WHERE userid = ? AND status = 'NEW'",
            Integer.class,
            userId);
    int rejected =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM orders WHERE userid = ? AND status = 'REJECTED'",
            Integer.class,
            userId);
    assertThat(accepted).isOne();
    assertThat(rejected).isOne();
  }

  @Test
  void marketSaleUpdatesPortfolioAndNewSaleReservesOnlyAvailableShares() throws Exception {
    int userId = createUserWithCash(100);
    int instrumentId = createInstrument("ACCIONES", "ORD-SLL");
    jdbcTemplate.update(
        "INSERT INTO orders (instrumentid, userid, size, price, type, side, status, datetime)"
            + " VALUES (?, ?, 5, 10, 'MARKET', 'BUY', 'FILLED', '2026-01-01')",
        instrumentId,
        userId);
    jdbcTemplate.update(
        "INSERT INTO marketdata (instrumentid, date, close) VALUES (?, CURRENT_DATE, 9),"
            + " (?, CURRENT_DATE, 10)",
        instrumentId,
        instrumentId);

    HttpResponse<String> sold =
        submit(
            userId,
            "market-sale",
            orderBody(instrumentId, "SELL", "MARKET", "\"amount\":20.00", null));
    HttpResponse<String> pending =
        submit(
            userId,
            "limit-sale",
            orderBody(instrumentId, "SELL", "LIMIT", "\"size\":2", "\"price\":12.00"));
    HttpRequest portfolioRequest =
        HttpRequest.newBuilder()
            .uri(
                URI.create(
                    "http://localhost:"
                        + port
                        + "/api/v1/users/"
                        + userId
                        + "/portfolio?accountNumber="
                        + accountNumber(userId)))
            .GET()
            .build();
    HttpResponse<String> portfolio =
        client.send(portfolioRequest, HttpResponse.BodyHandlers.ofString());

    assertThat(sold.statusCode()).isEqualTo(201);
    assertThat(mapper.readTree(sold.body()).path("price").decimalValue())
        .isEqualByComparingTo("10");
    assertThat(pending.statusCode()).isEqualTo(201);
    assertThat(portfolio.statusCode()).isEqualTo(200);
    JsonNode instrument = mapper.readTree(portfolio.body()).path("instruments").get(0);
    assertThat(instrument.path("quantity").asInt()).isEqualTo(3);
    assertThat(instrument.path("availableQuantity").asInt()).isOne();
  }

  @Test
  void technicalFailureRollsBackOrderAndIdempotencyTogether() throws Exception {
    int userId = createUserWithCash(100);
    int instrumentId = createInstrument("ACCIONES", "ORD-RBK");
    jdbcTemplate.execute(
        """
        CREATE FUNCTION fail_idempotency_insert() RETURNS trigger LANGUAGE plpgsql AS $$
        BEGIN
          IF NEW.idempotencykey = 'rollback-key' THEN
            RAISE EXCEPTION 'forced idempotency failure';
          END IF;
          RETURN NEW;
        END
        $$;
        CREATE TRIGGER fail_idempotency_insert
          BEFORE INSERT ON order_idempotency
          FOR EACH ROW EXECUTE FUNCTION fail_idempotency_insert();
        """);

    HttpResponse<String> response;
    try {
      response =
          submit(
              userId,
              "rollback-key",
              orderBody(instrumentId, "BUY", "LIMIT", "\"size\":1", "\"price\":10"));
    } finally {
      jdbcTemplate.execute(
          "DROP TRIGGER fail_idempotency_insert ON order_idempotency;"
              + " DROP FUNCTION fail_idempotency_insert();");
    }

    assertThat(response.statusCode()).isEqualTo(500);
    assertThat(countOrders(userId)).isOne();
    assertThat(countIdempotent(userId, "rollback-key")).isZero();
  }

  @Test
  void missingInstrumentDoesNotConsumeKeyAndInvalidInputCreatesNothing() throws Exception {
    int userId = createUserWithCash(100);

    HttpResponse<String> missing =
        submit(userId, "unused-key", orderBody(999_999, "BUY", "MARKET", "\"size\":1", null));
    HttpResponse<String> invalid =
        submit(
            userId,
            "invalid-key",
            orderBody(31, "BUY", "MARKET", "\"size\":1,\"amount\":10", null));

    assertThat(missing.statusCode()).isEqualTo(404);
    assertThat(invalid.statusCode()).isEqualTo(400);
    assertThat(countIdempotent(userId, "unused-key")).isZero();
    assertThat(countIdempotent(userId, "invalid-key")).isZero();
    assertThat(countOrders(userId)).isEqualTo(1);
  }

  @Test
  void documentsOrderContractInOpenApi() throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/v3/api-docs"))
            .GET()
            .build();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    JsonNode operation =
        mapper
            .readTree(response.body())
            .path("paths")
            .path("/api/v1/users/{userId}/orders")
            .path("post");
    assertThat(operation.isMissingNode()).isFalse();
    assertThat(operation.path("parameters").toString()).contains("Idempotency-Key");
    assertThat(operation.path("responses").toString())
        .contains("\"201\"", "\"400\"", "\"404\"", "\"409\"", "\"422\"");
  }

  private HttpResponse<String> submit(int userId, String key, String body) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/users/" + userId + "/orders"))
            .header("Content-Type", "application/json")
            .header("Idempotency-Key", key)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> uncheckedSubmit(int userId, String key, String body) {
    try {
      return submit(userId, key, body);
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  private int createUserWithCash(int cash) {
    String account = UUID.randomUUID().toString().substring(0, 20);
    int userId =
        jdbcTemplate.queryForObject(
            "INSERT INTO users (accountnumber) VALUES (?) RETURNING id", Integer.class, account);
    jdbcTemplate.update(
        "INSERT INTO orders (instrumentid, userid, size, price, side, status, type, datetime)"
            + " VALUES (66, ?, ?, 1, 'CASH_IN', 'FILLED', 'MARKET', ?)",
        userId,
        cash,
        LocalDateTime.parse("2026-01-01T10:00:00"));
    return userId;
  }

  private int createInstrument(String type, String prefix) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO instruments (ticker, name, type) VALUES (?, ?, ?) RETURNING id",
        Integer.class,
        prefix.substring(0, Math.min(10, prefix.length())),
        prefix,
        type);
  }

  private int countOrders(int userId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM orders WHERE userid = ?", Integer.class, userId);
  }

  private int countIdempotent(int userId, String key) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM order_idempotency WHERE userid = ? AND idempotencykey = ?",
        Integer.class,
        userId,
        key);
  }

  private String accountNumber(int userId) {
    return jdbcTemplate.queryForObject(
        "SELECT accountnumber FROM users WHERE id = ?", String.class, userId);
  }

  private String orderBody(int instrumentId, String side, String type, String size, String price) {
    String pricePart = price == null ? "" : "," + price;
    return "{\"instrumentId\":"
        + instrumentId
        + ",\"side\":\""
        + side
        + "\",\"type\":\""
        + type
        + "\","
        + size
        + pricePart
        + "}";
  }
}
