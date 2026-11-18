package com.cocobank.challenge.adapter.out.persistence.adapter;

import com.cocobank.challenge.adapter.out.persistence.entity.OrderIdempotencyEntity;
import com.cocobank.challenge.adapter.out.persistence.entity.OrderIdempotencyId;
import com.cocobank.challenge.adapter.out.persistence.repository.OrderIdempotencyRepository;
import com.cocobank.challenge.application.dto.IdempotentSubmission;
import com.cocobank.challenge.application.dto.OrderSubmissionResult;
import com.cocobank.challenge.application.port.out.OrderIdempotencyPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class OrderIdempotencyPersistenceAdapter implements OrderIdempotencyPort {

  private final OrderIdempotencyRepository repository;
  private final ObjectMapper objectMapper;

  public OrderIdempotencyPersistenceAdapter(OrderIdempotencyRepository repository) {
    this.repository = repository;
    this.objectMapper = new ObjectMapper().findAndRegisterModules();
  }

  @Override
  public Optional<IdempotentSubmission> find(int userId, String idempotencyKey) {
    return repository
        .findById(new OrderIdempotencyId(userId, idempotencyKey))
        .map(entity ->
            new IdempotentSubmission(
                entity.getRequestFingerprint(),
                entity.getResponseStatus(),
                readResult(entity.getResponseBody())));
  }

  @Override
  public void save(
      int userId,
      String idempotencyKey,
      String fingerprint,
      int responseStatus,
      OrderSubmissionResult result) {
    try {
      repository.save(
          new OrderIdempotencyEntity(
              new OrderIdempotencyId(userId, idempotencyKey),
              fingerprint,
              responseStatus,
              objectMapper.writeValueAsString(result),
              result.order().id()));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(
          "No fue posible serializar el resultado de la orden", exception);
    }
  }

  private OrderSubmissionResult readResult(String body) {
    try {
      return objectMapper.readValue(body, OrderSubmissionResult.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(
          "No fue posible recuperar el resultado de idempotencia", exception);
    }
  }
}
