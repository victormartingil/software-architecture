package dev.reference.commerce.orders.application.port.input;

import dev.reference.commerce.orders.application.model.*;
import dev.reference.commerce.orders.domain.Order;
import dev.reference.commerce.shared.domain.Actor;
import java.util.UUID;

public interface OrderUseCase {
  OrderResult create(Actor actor, String idempotencyKey, CreateOrderCommand command);

  OrderResult get(Actor actor, UUID id);

  OrderPage list(Actor actor, OrderQuery query);

  Order visible(Actor actor, UUID id, boolean lock);

  void refund(UUID id);
}
