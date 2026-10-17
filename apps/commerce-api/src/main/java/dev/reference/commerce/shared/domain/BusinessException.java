package dev.reference.commerce.shared.domain;

public class BusinessException extends RuntimeException {
  public enum Kind {
    INVALID_INPUT,
    NOT_FOUND,
    CONFLICT,
    FORBIDDEN
  }

  private final Kind kind;

  public BusinessException(Kind kind, String message) {
    super(message);
    this.kind = kind;
  }

  public Kind kind() {
    return kind;
  }
}
