package dev.reference.commerce.returns.application.service;

/** Negative dependencies plus one allowed collaboration through an input port. */
public final class ApplicationBoundaryFixtures {
  public static class Adapter {
    dev.reference.commerce.returns.infrastructure.JdbcReturnRepositoryAdapter forbidden;
  }

  public static class ForeignRepository {
    dev.reference.commerce.orders.application.port.output.OrderRepository forbidden;
  }

  public static class InputPort {
    dev.reference.commerce.orders.application.port.input.OrderUseCase allowed;
  }
}
