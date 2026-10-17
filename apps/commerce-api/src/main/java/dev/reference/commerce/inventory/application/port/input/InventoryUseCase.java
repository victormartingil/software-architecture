package dev.reference.commerce.inventory.application.port.input;

import dev.reference.commerce.inventory.domain.Stock;

public interface InventoryUseCase {
  Stock get(String tenant, String sku);

  int reserve(String tenant, String sku, int quantity);

  void restore(String tenant, String sku, int quantity);
}
