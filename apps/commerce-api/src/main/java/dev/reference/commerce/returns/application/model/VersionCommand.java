package dev.reference.commerce.returns.application.model;

import jakarta.validation.constraints.*;

public record VersionCommand(@NotNull @Min(0) Long version) {}
