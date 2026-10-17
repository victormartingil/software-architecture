package dev.reference.commerce.orders.application.model;

import dev.reference.commerce.orders.domain.Order;
import dev.reference.commerce.orders.domain.OrderStatus;
import java.util.UUID;

public record OrderResult(
    UUID id, String sku, int quantity, int totalCents, OrderStatus status, long version) {
  public static OrderResult from(Order order) {
    return new OrderResult(
        order.id(),
        order.sku(),
        order.quantity(),
        order.totalCents(),
        order.status(),
        order.version());
  }
}
