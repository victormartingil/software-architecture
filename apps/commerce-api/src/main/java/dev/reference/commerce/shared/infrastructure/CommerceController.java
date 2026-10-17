package dev.reference.commerce.shared.infrastructure;

import dev.reference.commerce.inventory.application.port.input.InventoryUseCase;
import dev.reference.commerce.inventory.domain.Stock;
import dev.reference.commerce.orders.application.model.*;
import dev.reference.commerce.orders.application.port.input.OrderUseCase;
import dev.reference.commerce.returns.application.model.*;
import dev.reference.commerce.returns.application.port.input.ReturnUseCase;
import dev.reference.commerce.returns.domain.ReturnProposal;
import dev.reference.commerce.shared.domain.Actor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** HTTP adapter for the order, inventory and return use cases; it does not issue tokens. */
@RestController
@Validated
@RequestMapping("/api/v1")
public class CommerceController {
  private final OrderUseCase orders;
  private final InventoryUseCase inventory;
  private final ReturnUseCase returns;

  public CommerceController(OrderUseCase o, InventoryUseCase i, ReturnUseCase r) {
    orders = o;
    inventory = i;
    returns = r;
  }

  private Actor actor(Jwt j) {
    Object realm = j.getClaim("realm_access");
    Object roles = realm instanceof java.util.Map<?, ?> map ? map.get("roles") : null;
    return new Actor(
        j.getClaimAsString("tenant"),
        j.getSubject(),
        roles instanceof java.util.Collection<?> c && c.contains("MANAGER"));
  }

  @GetMapping("/inventory/{sku}")
  Stock stock(@AuthenticationPrincipal Jwt j, @PathVariable String sku) {
    return inventory.get(actor(j).tenant(), sku);
  }

  @PostMapping("/orders")
  @ResponseStatus(HttpStatus.CREATED)
  OrderResult create(
      @AuthenticationPrincipal Jwt j,
      @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
      @RequestBody @Valid CreateOrderCommand c) {
    return orders.create(actor(j), key, c);
  }

  @GetMapping("/orders/{id}")
  OrderResult get(@AuthenticationPrincipal Jwt j, @PathVariable UUID id) {
    return orders.get(actor(j), id);
  }

  @PostMapping("/returns")
  @ResponseStatus(HttpStatus.CREATED)
  ReturnProposal proposal(
      @AuthenticationPrincipal Jwt j,
      @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
      @RequestBody @Valid CreateReturnCommand c) {
    return returns.create(actor(j), key, c);
  }

  @GetMapping("/returns/{id}")
  ReturnProposal proposal(@AuthenticationPrincipal Jwt j, @PathVariable UUID id) {
    return returns.get(actor(j), id);
  }

  @PostMapping("/returns/{id}/approve")
  ReturnProposal approve(
      @AuthenticationPrincipal Jwt j, @PathVariable UUID id, @RequestBody @Valid VersionCommand c) {
    return returns.approve(actor(j), id, c.version());
  }

  @PostMapping("/returns/{id}/execute")
  ReturnProposal execute(
      @AuthenticationPrincipal Jwt j,
      @PathVariable UUID id,
      @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
      @RequestBody @Valid VersionCommand c) {
    return returns.execute(actor(j), id, key, c.version());
  }
}
