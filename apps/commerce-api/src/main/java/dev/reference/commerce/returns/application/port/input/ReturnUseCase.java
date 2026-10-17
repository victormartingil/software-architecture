package dev.reference.commerce.returns.application.port.input;

import dev.reference.commerce.returns.application.model.*;
import dev.reference.commerce.returns.domain.ReturnProposal;
import dev.reference.commerce.shared.domain.Actor;
import java.util.UUID;

public interface ReturnUseCase {
  ReturnProposal create(Actor a, String key, CreateReturnCommand c);

  ReturnProposal get(Actor a, UUID id);

  ReturnProposal approve(Actor a, UUID id, long version);

  ReturnProposal execute(Actor a, UUID id, String key, long version);
}
