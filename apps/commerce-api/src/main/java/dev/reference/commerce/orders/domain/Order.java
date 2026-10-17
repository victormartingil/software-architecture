package dev.reference.commerce.orders.domain;

import dev.reference.commerce.shared.domain.BusinessException;
import dev.reference.commerce.shared.domain.Quantity;
import java.util.UUID;

public record Order(
    UUID id,
    String tenant,
    String actor,
    String sku,
    int quantity,
    int totalCents,
    OrderStatus status,
    long version) {
  public Order {
    new Quantity(quantity);
    java.util.Objects.requireNonNull(status);
  }

  public void requireReturnEligible(String subject) {
    if (!actor.equals(subject) || status != OrderStatus.PLACED)
      throw new BusinessException(BusinessException.Kind.CONFLICT, "Order not eligible");
  }
}
