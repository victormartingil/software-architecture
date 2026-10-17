package dev.reference.commerce.inventory.application.service;

import dev.reference.commerce.inventory.application.port.input.InventoryUseCase;
import dev.reference.commerce.inventory.application.port.output.InventoryRepository;
import dev.reference.commerce.inventory.domain.Stock;
import dev.reference.commerce.shared.domain.Quantity;
import org.springframework.stereotype.Service;

@Service
public class InventoryService implements InventoryUseCase {
  private final InventoryRepository repository;

  public InventoryService(InventoryRepository repository) {
    this.repository = repository;
  }

  public Stock get(String tenant, String sku) {
    return repository.get(tenant, sku);
  }

  public int reserve(String tenant, String sku, int quantity) {
    new Quantity(quantity);
    return repository.reserve(tenant, sku, quantity);
  }

  public void restore(String tenant, String sku, int quantity) {
    new Quantity(quantity);
    repository.restore(tenant, sku, quantity);
  }
}
