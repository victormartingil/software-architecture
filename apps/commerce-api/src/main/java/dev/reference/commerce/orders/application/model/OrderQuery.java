package dev.reference.commerce.orders.application.model;

import dev.reference.commerce.orders.domain.OrderStatus;
import dev.reference.commerce.shared.domain.BusinessException;
import java.util.UUID;

public record OrderQuery(int limit, UUID after, OrderStatus status) {
  public OrderQuery {
    if (limit < 1 || limit > 50)
      throw new BusinessException(
          BusinessException.Kind.INVALID_INPUT, "Limit must be between 1 and 50");
  }
}
