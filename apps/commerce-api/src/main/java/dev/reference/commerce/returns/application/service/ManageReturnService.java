package dev.reference.commerce.returns.application.service;

import dev.reference.commerce.inventory.application.port.input.InventoryUseCase;
import dev.reference.commerce.orders.application.port.input.OrderUseCase;
import dev.reference.commerce.returns.application.model.*;
import dev.reference.commerce.returns.application.port.input.ReturnUseCase;
import dev.reference.commerce.returns.application.port.output.ReturnRepository;
import dev.reference.commerce.returns.domain.ReturnProposal;
import dev.reference.commerce.shared.application.Idempotency;
import dev.reference.commerce.shared.domain.*;
import java.time.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates, approves and executes return proposals; Commerce owns every business transition. */
@Service
public class ManageReturnService implements ReturnUseCase {
  private final ReturnRepository proposals;
  private final OrderUseCase orders;
  private final InventoryUseCase inventory;
  private final Idempotency idempotency;
  private final Clock clock;

  public ManageReturnService(
      ReturnRepository proposals,
      OrderUseCase orders,
      InventoryUseCase inventory,
      Idempotency idempotency,
      Clock clock) {
    this.proposals = proposals;
    this.orders = orders;
    this.inventory = inventory;
    this.idempotency = idempotency;
    this.clock = clock;
  }

  @Transactional
  public ReturnProposal create(Actor actor, String idempotencyKey, CreateReturnCommand command) {
    return idempotency.execute(
        actor.tenant() + ":" + actor.subject() + ":proposal:" + idempotencyKey,
        command,
        ReturnProposal.class,
        () -> {
          var order = orders.visible(actor, command.orderId(), true);
          if (!order.actor().equals(actor.subject()) || !order.status().equals("PLACED"))
            throw new BusinessException(409, "Order not eligible");
          var proposal =
              new ReturnProposal(
                  UUID.randomUUID(),
                  actor.tenant(),
                  actor.subject(),
                  order.id(),
                  command.reason(),
                  "PENDING",
                  0,
                  null);
          proposals.save(proposal);
          return proposal;
        });
  }

  public ReturnProposal get(Actor actor, UUID id) {
    return visible(actor, id, false);
  }

  private ReturnProposal visible(Actor actor, UUID id, boolean lock) {
    return proposals
        .find(id, lock)
        .filter(
            proposal -> proposal.tenant().equals(actor.tenant()) && (actor.manager() || proposal.actor().equals(actor.subject())))
        .orElseThrow(() -> new BusinessException(404, "Return not found"));
  }

  private void manager(Actor actor) {
    if (!actor.manager()) throw new BusinessException(403, "Manager role required");
  }

  @Transactional
  public ReturnProposal approve(Actor actor, UUID id, long version) {
    manager(actor);
    var proposal = visible(actor, id, true);
    if (proposal.version() != version || proposal.status().equals("EXECUTED"))
      throw new BusinessException(409, "Proposal version/state changed");
    proposals.approve(id, actor.subject(), clock.instant().plusSeconds(600));
    return visible(actor, id, false);
  }

  @Transactional
  public ReturnProposal execute(Actor actor, UUID id, String idempotencyKey, long version) {
    manager(actor);
    return idempotency.execute(
        actor.tenant() + ":" + actor.subject() + ":execute:" + id + ":" + idempotencyKey,
        version,
        ReturnProposal.class,
        () -> {
          var proposal = visible(actor, id, true);
          if (proposal.version() != version
              || !proposal.status().equals("APPROVED")
              || proposal.approvedUntil() == null
              || !clock.instant().isBefore(proposal.approvedUntil()))
            throw new BusinessException(409, "Approval missing, expired or changed");
          var order = orders.visible(actor, proposal.orderId(), true);
          orders.refund(order.id());
          inventory.restore(actor.tenant(), order.sku(), order.quantity());
          proposals.executed(id);
          return visible(actor, id, false);
        });
  }
}
