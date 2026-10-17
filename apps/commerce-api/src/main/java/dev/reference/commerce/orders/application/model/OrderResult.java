package dev.reference.commerce.orders.application.model;

import dev.reference.commerce.orders.domain.Order;
import java.util.UUID;

public record OrderResult(
    UUID id, String sku, int quantity, int totalCents, String status, long version) {
  public static OrderResult from(Order o) {
    return new OrderResult(o.id(), o.sku(), o.quantity(), o.totalCents(), o.status(), o.version());
  }
}
