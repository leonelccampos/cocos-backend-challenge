package com.cocobank.challenge.application.dto;

import com.cocobank.challenge.domain.order.OrderSide;
import com.cocobank.challenge.domain.order.OrderType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public record SubmitOrderCommand(
    int userId,
    int instrumentId,
    OrderSide side,
    OrderType type,
    Integer size,
    BigDecimal amount,
    BigDecimal price,
    String idempotencyKey) {

  public String fingerprint() {
    String canonical =
        String.join(
            "|",
            Integer.toString(instrumentId),
            side.name(),
            type.name(),
            size == null ? "" : size.toString(),
            normalized(amount),
            normalized(price));
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static String normalized(BigDecimal value) {
    return value == null ? "" : value.stripTrailingZeros().toPlainString();
  }
}
