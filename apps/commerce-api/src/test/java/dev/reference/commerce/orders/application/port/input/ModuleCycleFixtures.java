package dev.reference.commerce.orders.application.port.input;
/** Counterexamples excluded from production imports. */
public final class ModuleCycleFixtures {
  public static class First { dev.reference.commerce.returns.application.port.input.ModuleCycleFixtures.First peer; }
  public static class Second { dev.reference.commerce.returns.application.port.input.ModuleCycleFixtures.Second peer; }
}
