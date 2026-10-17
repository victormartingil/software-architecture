package dev.reference.commerce.orders.domain;

import java.util.UUID;

public record Order(
    UUID id,
    String tenant,
    String actor,
    String sku,
    int quantity,
    int totalCents,
    String status,
    long version) {}
