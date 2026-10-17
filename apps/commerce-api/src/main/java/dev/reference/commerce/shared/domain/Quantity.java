package dev.reference.commerce.shared.domain;

/** A bounded business quantity; valid even when no HTTP controller is involved. */
public record Quantity(int value) {
  public Quantity {
    if (value < 1 || value > 100)
      throw new BusinessException(
          BusinessException.Kind.INVALID_INPUT, "Quantity must be between 1 and 100");
  }
}
