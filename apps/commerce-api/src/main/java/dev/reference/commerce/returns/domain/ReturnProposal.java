package dev.reference.commerce.returns.domain;

import java.time.Instant;
import java.util.UUID;

public record ReturnProposal(
    UUID id,
    String tenant,
    String actor,
    UUID orderId,
    String reason,
    String status,
    long version,
    Instant approvedUntil) {}
