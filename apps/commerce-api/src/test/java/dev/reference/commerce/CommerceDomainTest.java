package dev.reference.commerce;

import static org.assertj.core.api.Assertions.*;

import dev.reference.commerce.orders.application.model.*;
import dev.reference.commerce.orders.domain.*;
import dev.reference.commerce.returns.domain.*;
import dev.reference.commerce.shared.domain.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CommerceDomainTest {
  @Test
  void quantityCannotBypassHttpValidation() {
    for (int invalid : new int[] {Integer.MIN_VALUE, -1, 0, 101, Integer.MAX_VALUE}) {
      assertThatThrownBy(() -> new Quantity(invalid)).isInstanceOf(BusinessException.class);
      assertThatThrownBy(() -> new CreateOrderCommand("BOOK-1", invalid))
          .isInstanceOf(BusinessException.class);
      assertThatThrownBy(
              () ->
                  new Order(
                      UUID.randomUUID(),
                      "demo",
                      "alice",
                      "BOOK-1",
                      invalid,
                      2500,
                      OrderStatus.PLACED,
                      0))
          .isInstanceOf(BusinessException.class);
    }
    assertThat(new Quantity(1).value()).isEqualTo(1);
    assertThat(new Quantity(100).value()).isEqualTo(100);
  }

  @Test
  void statesProtectTransitionsWithoutSpring() {
    var order =
        new Order(UUID.randomUUID(), "demo", "alice", "BOOK-1", 1, 2500, OrderStatus.REFUNDED, 1);
    assertThatThrownBy(() -> order.requireReturnEligible("alice")).hasMessage("Order not eligible");
    var now = Instant.parse("2026-10-17T12:00:00Z");
    var approved =
        new ReturnProposal(
            UUID.randomUUID(),
            "demo",
            "alice",
            order.id(),
            "Unopened",
            ReturnStatus.APPROVED,
            1,
            now);
    assertThatThrownBy(() -> approved.requireExecutable(1, now)).hasMessageContaining("expired");
    assertThatThrownBy(() -> approved.requireExecutable(0, now.minusSeconds(1)))
        .hasMessageContaining("changed");
    approved.requireExecutable(1, now.minusSeconds(1));
    var pending =
        new ReturnProposal(
            approved.id(), "demo", "alice", order.id(), "Unopened", ReturnStatus.PENDING, 0, null);
    assertThatThrownBy(() -> pending.requireExecutable(0, now)).hasMessageContaining("Approval");
    var executed =
        new ReturnProposal(
            approved.id(), "demo", "alice", order.id(), "Unopened", ReturnStatus.EXECUTED, 2, now);
    assertThatThrownBy(() -> executed.requireApprovable(2)).hasMessageContaining("state");
    assertThatThrownBy(() -> new OrderQuery(51, null, null)).isInstanceOf(BusinessException.class);
  }
}
