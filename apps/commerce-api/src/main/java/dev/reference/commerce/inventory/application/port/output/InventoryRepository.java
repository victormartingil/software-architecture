package dev.reference.commerce.inventory.application.port.output;

import dev.reference.commerce.inventory.domain.Stock;

public interface InventoryRepository {
  Stock get(String tenant, String sku);

  int reserve(String tenant, String sku, int quantity);

  void restore(String tenant, String sku, int quantity);
}
