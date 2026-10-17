package dev.reference.commerce.returns.application.model;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record CreateReturnCommand(
    @NotNull UUID orderId, @NotBlank @Size(max = 500) String reason) {}
