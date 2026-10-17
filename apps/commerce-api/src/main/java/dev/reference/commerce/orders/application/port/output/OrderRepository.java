package dev.reference.commerce.orders.application.port.output;

import dev.reference.commerce.orders.application.model.OrderQuery;
import dev.reference.commerce.orders.domain.Order;
import dev.reference.commerce.shared.domain.Actor;
import java.util.*;

public interface OrderRepository {
  void save(Order order);

  List<Order> list(Actor actor, OrderQuery query);

  Optional<Order> find(UUID id, boolean lock);

  void refund(UUID id);
}
