package dev.reference.commerce.inventory.domain;

public record Stock(String sku, int available, long version) {}
