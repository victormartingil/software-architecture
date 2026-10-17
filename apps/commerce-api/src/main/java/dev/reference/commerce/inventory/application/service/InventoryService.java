package dev.reference.commerce.inventory.application.service;

import dev.reference.commerce.inventory.application.port.input.InventoryUseCase;
import dev.reference.commerce.inventory.application.port.output.InventoryRepository;
import dev.reference.commerce.inventory.domain.Stock;
import org.springframework.stereotype.Service;

@Service
public class InventoryService implements InventoryUseCase {
  private final InventoryRepository repository;

  public InventoryService(InventoryRepository r) {
    repository = r;
  }

  public Stock get(String t, String s) {
    return repository.get(t, s);
  }

  public int reserve(String t, String s, int q) {
    return repository.reserve(t, s, q);
  }

  public void restore(String t, String s, int q) {
    repository.restore(t, s, q);
  }
}
