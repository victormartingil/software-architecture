package dev.reference.commerce.orders.application.service;

import dev.reference.commerce.inventory.application.port.input.InventoryUseCase;
import dev.reference.commerce.orders.application.model.*;
import dev.reference.commerce.orders.application.port.input.OrderUseCase;
import dev.reference.commerce.orders.application.port.output.OrderRepository;
import dev.reference.commerce.orders.domain.Order;
import dev.reference.commerce.orders.domain.OrderStatus;
import dev.reference.commerce.shared.application.*;
import dev.reference.commerce.shared.domain.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coordinates order creation, visibility and refund state through the cohesive order input port.
 */
@Service
public class ManageOrderService implements OrderUseCase {
  private final OrderRepository orders;
  private final InventoryUseCase inventory;
  private final Idempotency idempotency;
  private final Events events;

  public ManageOrderService(
      OrderRepository orderRepository,
      InventoryUseCase inventoryUseCase,
      Idempotency idempotencyStore,
      Events orderEvents) {
    orders = orderRepository;
    inventory = inventoryUseCase;
    idempotency = idempotencyStore;
    events = orderEvents;
  }

  @Transactional
  public OrderResult create(Actor actor, String idempotencyKey, CreateOrderCommand command) {
    return idempotency.execute(
        new Idempotency.Scope(actor.tenant(), actor.subject(), "order", "", idempotencyKey),
        command,
        OrderResult.class,
        () -> {
          int price = inventory.reserve(actor.tenant(), command.sku(), command.quantity());
          var order =
              new Order(
                  UUID.randomUUID(),
                  actor.tenant(),
                  actor.subject(),
                  command.sku(),
                  command.quantity(),
                  Math.multiplyExact(price, command.quantity()),
                  OrderStatus.PLACED,
                  0);
          orders.save(order);
          events.placed(
              order.id(), order.tenant(), order.sku(), order.quantity(), order.totalCents());
          return OrderResult.from(order);
        });
  }

  public OrderPage list(Actor actor, OrderQuery query) {
    var candidates = orders.list(actor, query);
    boolean hasMore = candidates.size() > query.limit();
    var items = candidates.stream().limit(query.limit()).map(OrderResult::from).toList();
    return new OrderPage(items, hasMore ? items.getLast().id() : null);
  }

  public OrderResult get(Actor actor, UUID id) {
    return OrderResult.from(visible(actor, id, false));
  }

  public Order visible(Actor actor, UUID id, boolean lock) {
    return orders
        .find(id, lock)
        .filter(
            order ->
                order.tenant().equals(actor.tenant())
                    && (actor.manager() || order.actor().equals(actor.subject())))
        .orElseThrow(
            () -> new BusinessException(BusinessException.Kind.NOT_FOUND, "Order not found"));
  }

  public void refund(UUID id) {
    orders.refund(id);
  }
}
