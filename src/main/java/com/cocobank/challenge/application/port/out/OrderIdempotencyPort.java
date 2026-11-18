package com.cocobank.challenge.application.port.out;

import com.cocobank.challenge.application.dto.IdempotentSubmission;
import com.cocobank.challenge.application.dto.OrderSubmissionResult;
import java.util.Optional;

public interface OrderIdempotencyPort {

  Optional<IdempotentSubmission> find(int userId, String idempotencyKey);

  void save(
      int userId,
      String idempotencyKey,
      String fingerprint,
      int responseStatus,
      OrderSubmissionResult result);
}
