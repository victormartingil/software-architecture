package dev.reference.commerce.inventory.infrastructure.rest;

import dev.reference.commerce.inventory.application.port.input.InventoryUseCase;
import dev.reference.commerce.inventory.domain.Stock;
import dev.reference.commerce.shared.infrastructure.security.JwtActor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1")
public class InventoryController {
  private final InventoryUseCase inventory;

  public InventoryController(InventoryUseCase inventory) {
    this.inventory = inventory;
  }

  @GetMapping("/inventory/{sku}")
  Stock stock(@AuthenticationPrincipal Jwt jwt, @PathVariable String sku) {
    return inventory.get(JwtActor.from(jwt).tenant(), sku);
  }
}
