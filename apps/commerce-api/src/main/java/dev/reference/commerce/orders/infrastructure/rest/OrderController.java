package dev.reference.commerce.orders.infrastructure.rest;

import dev.reference.commerce.orders.application.model.*;
import dev.reference.commerce.orders.application.port.input.OrderUseCase;
import dev.reference.commerce.orders.domain.OrderStatus;
import dev.reference.commerce.shared.infrastructure.security.JwtActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1")
public class OrderController {
  private final OrderUseCase orders;

  public OrderController(OrderUseCase orders) {
    this.orders = orders;
  }

  @PostMapping("/orders")
  @ResponseStatus(HttpStatus.CREATED)
  OrderResult create(
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
      @RequestBody @Valid CreateOrderCommand command) {
    return orders.create(JwtActor.from(jwt), idempotencyKey, command);
  }

  @GetMapping("/orders")
  OrderPage list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "10") int limit,
      @RequestParam(required = false)
          @Pattern(
              regexp =
                  "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
          String after,
      @RequestParam(required = false) OrderStatus status) {
    return orders.list(
        JwtActor.from(jwt),
        new OrderQuery(limit, after == null ? null : UUID.fromString(after), status));
  }

  @GetMapping("/orders/{id}")
  OrderResult get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return orders.get(JwtActor.from(jwt), id);
  }
}
