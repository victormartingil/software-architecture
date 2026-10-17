package dev.reference.commerce.shared.application;

import java.util.UUID;

public interface Events {
  void placed(UUID id, String tenant, String sku, int quantity, int totalCents);
}
