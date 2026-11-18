package com.cocobank.challenge.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cocobank.challenge.application.dto.CancelOrderCommand;
import com.cocobank.challenge.application.exception.OrderBadRequestException;
import com.cocobank.challenge.application.exception.OrderNotFoundException;
import com.cocobank.challenge.application.port.out.OrderPort;
import com.cocobank.challenge.application.port.out.UserPort;
import com.cocobank.challenge.domain.order.Order;
import com.cocobank.challenge.domain.order.OrderNotCancellableException;
import com.cocobank.challenge.domain.order.OrderSide;
import com.cocobank.challenge.domain.order.OrderStatus;
import com.cocobank.challenge.domain.order.OrderType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CancelOrderUseCaseTest {

  @Mock private UserPort userPort;
  @Mock private OrderPort orderPort;

  private CancelOrderUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new CancelOrderUseCase(userPort, orderPort);
    lenient().when(userPort.lock(1)).thenReturn(true);
    lenient().when(orderPort.update(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void cancelsANewOrderAssociatedToTheUser() {
    when(orderPort.findById(41)).thenReturn(Optional.of(order(OrderStatus.NEW)));

    Order result = useCase.execute(new CancelOrderCommand(1, 41, "CANCELLED"));

    assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
    ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
    verify(orderPort).update(captor.capture());
    assertThat(captor.getValue().status()).isEqualTo(OrderStatus.CANCELLED);
  }

  @Test
  void rejectsMissingOrDifferentStatusWithoutLockingTheUser() {
    assertThatThrownBy(() -> useCase.execute(new CancelOrderCommand(1, 41, null)))
        .isInstanceOf(OrderBadRequestException.class);
    assertThatThrownBy(() -> useCase.execute(new CancelOrderCommand(1, 41, "NEW")))
        .isInstanceOf(OrderBadRequestException.class);

    verify(userPort, never()).lock(1);
    verify(orderPort, never()).findById(41);
  }

  @Test
  void raisesNotFoundWhenTheUserDoesNotExist() {
    when(userPort.lock(2)).thenReturn(false);

    assertThatThrownBy(() -> useCase.execute(new CancelOrderCommand(2, 41, "CANCELLED")))
        .isInstanceOf(OrderNotFoundException.class);

    verify(orderPort, never()).findById(any(Integer.class));
  }

  @Test
  void raisesNotFoundWhenTheOrderDoesNotExist() {
    when(orderPort.findById(41)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(new CancelOrderCommand(1, 41, "CANCELLED")))
        .isInstanceOf(OrderNotFoundException.class);

    verify(orderPort, never()).update(any());
  }

  @Test
  void raisesNotFoundWhenTheOrderBelongsToAnotherUser() {
    Order other =
        new Order(
            41, 2, 31, OrderSide.BUY, OrderType.LIMIT, 2, BigDecimal.TEN, OrderStatus.NEW, null);
    when(orderPort.findById(41)).thenReturn(Optional.of(other));

    assertThatThrownBy(() -> useCase.execute(new CancelOrderCommand(1, 41, "CANCELLED")))
        .isInstanceOf(OrderNotFoundException.class);

    verify(orderPort, never()).update(any());
  }

  @Test
  void raisesConflictWhenTheOrderIsNotNew() {
    when(orderPort.findById(41)).thenReturn(Optional.of(order(OrderStatus.FILLED)));

    assertThatThrownBy(() -> useCase.execute(new CancelOrderCommand(1, 41, "CANCELLED")))
        .isInstanceOf(OrderNotCancellableException.class);

    verify(orderPort, never()).update(any());
  }

  private Order order(OrderStatus status) {
    return new Order(
        41,
        1,
        31,
        OrderSide.BUY,
        OrderType.LIMIT,
        2,
        new BigDecimal("10.00"),
        status,
        LocalDateTime.parse("2026-01-02T12:00:00"));
  }
}
