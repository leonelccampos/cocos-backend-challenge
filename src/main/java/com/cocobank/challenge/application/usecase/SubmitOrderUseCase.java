package com.cocobank.challenge.application.usecase;

import com.cocobank.challenge.application.dto.CreateOrderData;
import com.cocobank.challenge.application.dto.OrderSubmissionResult;
import com.cocobank.challenge.application.dto.SubmitOrderCommand;
import com.cocobank.challenge.application.exception.IdempotencyConflictException;
import com.cocobank.challenge.application.exception.OrderBadRequestException;
import com.cocobank.challenge.application.exception.OrderNotFoundException;
import com.cocobank.challenge.application.port.out.InstrumentPort;
import com.cocobank.challenge.application.port.out.MarketDataPort;
import com.cocobank.challenge.application.port.out.OrderIdempotencyPort;
import com.cocobank.challenge.application.port.out.OrderPort;
import com.cocobank.challenge.application.port.out.UserPort;
import com.cocobank.challenge.domain.order.Order;
import com.cocobank.challenge.domain.order.OrderRejectionReason;
import com.cocobank.challenge.domain.order.OrderSide;
import com.cocobank.challenge.domain.order.OrderStatus;
import com.cocobank.challenge.domain.order.OrderType;
import com.cocobank.challenge.domain.portfolio.InstrumentPosition;
import com.cocobank.challenge.domain.portfolio.PortfolioCashSummary;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubmitOrderUseCase {

  private static final BigDecimal MAX_LIMIT_PRICE = new BigDecimal("99999999.99");
  private static final int MAX_SIZE = Integer.MAX_VALUE;

  private final UserPort userPort;
  private final InstrumentPort instrumentPort;
  private final MarketDataPort marketDataPort;
  private final OrderPort orderPort;
  private final OrderIdempotencyPort idempotencyPort;
  private final Clock clock;

  public SubmitOrderUseCase(
      UserPort userPort,
      InstrumentPort instrumentPort,
      MarketDataPort marketDataPort,
      OrderPort orderPort,
      OrderIdempotencyPort idempotencyPort,
      Clock clock) {
    this.userPort = userPort;
    this.instrumentPort = instrumentPort;
    this.marketDataPort = marketDataPort;
    this.orderPort = orderPort;
    this.idempotencyPort = idempotencyPort;
    this.clock = clock;
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public OrderSubmissionResult execute(SubmitOrderCommand command) {
    validateInput(command);
    verifyUser(command.userId());

    String fingerprint = command.fingerprint();
    var replay = findReplay(command, fingerprint);
    if (replay.isPresent()) {
      return replay.get();
    }

    verifyInstrument(command.instrumentId());

    BigDecimal referencePrice = resolveReferencePrice(command);

    if (referencePrice == null) {
      return rejectMissingMarketPrice(command, fingerprint);
    }

    int size = resolveSize(command, referencePrice);
    var orders = orderPort.findByUserId(command.userId());
    OrderRejectionReason rejection = validateAvailability(command, orders, size, referencePrice);
    Order order = save(command, size, referencePrice, determineStatus(command, rejection));
    OrderSubmissionResult result =
        rejection == null
            ? OrderSubmissionResult.accepted(order)
            : OrderSubmissionResult.rejected(order, rejection);
    return persist(command, fingerprint, result);
  }

  private OrderSubmissionResult persist(
      SubmitOrderCommand command, String fingerprint, OrderSubmissionResult result) {
    idempotencyPort.save(
        command.userId(),
        command.idempotencyKey(),
        fingerprint,
        result.isRejected() ? 422 : 201,
        result);
    return result;
  }

  private Order save(
      SubmitOrderCommand command, Integer size, BigDecimal price, OrderStatus status) {
    return orderPort.save(
        new CreateOrderData(
            command.userId(),
            command.instrumentId(),
            command.side(),
            command.type(),
            size,
            price,
            status,
            LocalDateTime.now(clock)));
  }

  private int resolveSize(SubmitOrderCommand command, BigDecimal referencePrice) {
    if (command.size() != null) {
      return command.size();
    }
    BigDecimal shares = command.amount().divide(referencePrice, 0, RoundingMode.FLOOR);
    if (shares.signum() <= 0) {
      throw new OrderBadRequestException("El monto no alcanza para una acción entera.");
    }
    if (shares.compareTo(BigDecimal.valueOf(MAX_SIZE)) > 0) {
      throw new OrderBadRequestException("La cantidad calculada excede el máximo permitido.");
    }
    return shares.intValueExact();
  }

  private OrderRejectionReason validateAvailability(
      SubmitOrderCommand command, List<Order> orders, int size, BigDecimal price) {
    if (command.side() == OrderSide.BUY) {
      BigDecimal available = PortfolioCashSummary.calculate(orders).available();
      BigDecimal required =
          command.amount() != null ? command.amount() : price.multiply(BigDecimal.valueOf(size));
      return required.compareTo(available) > 0 ? OrderRejectionReason.INSUFFICIENT_CASH : null;
    }
    int available =
        InstrumentPosition.calculate(command.instrumentId(), orders).availableQuantity();
    return size > available ? OrderRejectionReason.INSUFFICIENT_SHARES : null;
  }

  private void verifyUser(int userId) {
    if (!userPort.lock(userId)) {
      throw new OrderNotFoundException("No se encontró el usuario solicitado.");
    }
  }

  private java.util.Optional<OrderSubmissionResult> findReplay(
      SubmitOrderCommand command, String fingerprint) {
    var existing = idempotencyPort.find(command.userId(), command.idempotencyKey());
    if (existing.isEmpty()) {
      return java.util.Optional.empty();
    }
    if (!existing.get().fingerprint().equals(fingerprint)) {
      throw new IdempotencyConflictException();
    }
    return java.util.Optional.of(existing.get().result());
  }

  private BigDecimal resolveReferencePrice(SubmitOrderCommand command) {
    return command.type() == OrderType.LIMIT
        ? command.price()
        : marketDataPort
            .lastMarketData(command.instrumentId())
            .map(marketData -> marketData.latestClose())
            .orElse(null);
  }

  private void verifyInstrument(int instrumentId) {
    var instrument =
        instrumentPort
            .find(instrumentId)
            .orElseThrow(
                () -> new OrderNotFoundException("No se encontró el instrumento solicitado."));
    if (!instrument.isTradable()) {
      throw new OrderBadRequestException("Solo se admiten instrumentos de tipo ACCIONES.");
    }
  }

  private OrderSubmissionResult rejectMissingMarketPrice(
      SubmitOrderCommand command, String fingerprint) {
    return persist(
        command,
        fingerprint,
        OrderSubmissionResult.rejected(
            save(command, command.size(), null, OrderStatus.REJECTED),
            OrderRejectionReason.MARKET_PRICE_UNAVAILABLE));
  }

  private OrderStatus determineStatus(
      SubmitOrderCommand command, OrderRejectionReason rejectionReason) {
    if (rejectionReason != null) {
      return OrderStatus.REJECTED;
    }
    return command.type().acceptedStatus();
  }

  private void validateInput(SubmitOrderCommand command) {
    if (command.userId() <= 0
        || command.instrumentId() <= 0
        || command.side() == null
        || (command.side() != OrderSide.BUY && command.side() != OrderSide.SELL)
        || command.type() == null) {
      throw new OrderBadRequestException("La orden contiene identificadores o enums inválidos.");
    }
    if ((command.size() == null) == (command.amount() == null)) {
      throw new OrderBadRequestException("Debe indicar exactamente size o amount.");
    }
    if (command.size() != null && command.size() <= 0) {
      throw new OrderBadRequestException("size debe ser positivo.");
    }
    if (command.amount() != null && command.amount().signum() <= 0) {
      throw new OrderBadRequestException("amount debe ser positivo.");
    }
    if (command.type() == OrderType.MARKET && command.price() != null) {
      throw new OrderBadRequestException("MARKET no admite price.");
    }
    if (command.type() == OrderType.LIMIT) {
      BigDecimal price = command.price();
      if (price == null || price.signum() <= 0 || price.scale() > 2) {
        throw new OrderBadRequestException(
            "price límite debe ser positivo y tener hasta dos decimales.");
      }
      if (price.compareTo(MAX_LIMIT_PRICE) > 0) {
        throw new OrderBadRequestException("price límite excede el máximo permitido.");
      }
    }
  }
}
