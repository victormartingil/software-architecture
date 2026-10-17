package dev.reference.commerce.orders.application.port.output;

import dev.reference.commerce.orders.domain.Order;
import java.util.*;

public interface OrderRepository {
  void save(Order o);

  Optional<Order> find(UUID id, boolean lock);

  void refund(UUID id);
}
