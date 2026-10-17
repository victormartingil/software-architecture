package dev.reference.commerce.orders.application.port.input;

import dev.reference.commerce.orders.application.model.*;
import dev.reference.commerce.orders.domain.Order;
import dev.reference.commerce.shared.domain.Actor;
import java.util.UUID;

public interface OrderUseCase {
  OrderResult create(Actor a, String key, CreateOrderCommand c);

  OrderResult get(Actor a, UUID id);

  Order visible(Actor a, UUID id, boolean lock);

  void refund(UUID id);
}
