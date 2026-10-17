package dev.reference.commerce.returns.application.port.output;

import dev.reference.commerce.returns.domain.ReturnProposal;
import java.time.Instant;
import java.util.*;

public interface ReturnRepository {
  void save(ReturnProposal proposal);

  Optional<ReturnProposal> find(UUID id, boolean lock);

  void approve(UUID id, String actor, Instant until);

  void executed(UUID id);
}
