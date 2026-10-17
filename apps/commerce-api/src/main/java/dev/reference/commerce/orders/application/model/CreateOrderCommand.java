package dev.reference.commerce.orders.application.model;

import jakarta.validation.constraints.*;

public record CreateOrderCommand(
    @NotBlank @Size(max = 80) String sku, @Min(1) @Max(100) int quantity) {}
