package dev.reference.commerce.orders.application.model;

import java.util.List;
import java.util.UUID;

public record OrderPage(List<OrderResult> items, UUID nextCursor) {
  public OrderPage {
    items = List.copyOf(items);
  }
}
