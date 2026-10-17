package dev.reference.commerce.shared.application;

import java.util.Objects;
import java.util.function.Supplier;

public interface Idempotency {
  /** Each component is independent: delimiters in tenant, subject or key carry no meaning. */
  record Scope(String tenant, String subject, String operation, String resource, String key) {
    public Scope {
      Objects.requireNonNull(tenant);
      Objects.requireNonNull(subject);
      Objects.requireNonNull(operation);
      Objects.requireNonNull(resource);
      Objects.requireNonNull(key);
    }
  }

  <T> T execute(Scope scope, Object request, Class<T> type, Supplier<T> action);
}
