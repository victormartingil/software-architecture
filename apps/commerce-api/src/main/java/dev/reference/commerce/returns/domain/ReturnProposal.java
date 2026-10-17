package dev.reference.commerce.returns.domain;

import dev.reference.commerce.shared.domain.BusinessException;
import java.time.Instant;
import java.util.UUID;

public record ReturnProposal(
    UUID id,
    String tenant,
    String actor,
    UUID orderId,
    String reason,
    ReturnStatus status,
    long version,
    Instant approvedUntil) {
  public ReturnProposal {
    java.util.Objects.requireNonNull(status);
  }

  public void requireApprovable(long expectedVersion) {
    if (version != expectedVersion || status == ReturnStatus.EXECUTED)
      conflict("Proposal version/state changed");
  }

  public void requireExecutable(long expectedVersion, Instant now) {
    if (version != expectedVersion
        || status != ReturnStatus.APPROVED
        || approvedUntil == null
        || !now.isBefore(approvedUntil)) conflict("Approval missing, expired or changed");
  }

  private void conflict(String message) {
    throw new BusinessException(BusinessException.Kind.CONFLICT, message);
  }
}
