package dev.reference.commerce.returns.application.port.input;

import dev.reference.commerce.returns.application.model.*;
import dev.reference.commerce.returns.domain.ReturnProposal;
import dev.reference.commerce.shared.domain.Actor;
import java.util.UUID;

public interface ReturnUseCase {
  ReturnProposal create(Actor actor, String idempotencyKey, CreateReturnCommand command);

  ReturnProposal get(Actor actor, UUID id);

  ReturnProposal approve(Actor actor, UUID id, long version);

  ReturnProposal execute(Actor actor, UUID id, String idempotencyKey, long version);
}
