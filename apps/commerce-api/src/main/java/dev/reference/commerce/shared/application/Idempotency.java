package dev.reference.commerce.shared.application;

import java.util.function.Supplier;

public interface Idempotency {
  <T> T execute(String scope, Object request, Class<T> type, Supplier<T> action);
}
