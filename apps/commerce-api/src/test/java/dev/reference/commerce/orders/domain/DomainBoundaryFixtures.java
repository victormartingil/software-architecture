package dev.reference.commerce.orders.domain;

/** Deliberately invalid test-only classes, excluded from the production import. */
public final class DomainBoundaryFixtures {
  public static class Jackson2 {
    com.fasterxml.jackson.databind.ObjectMapper forbidden;
  }

  public static class Jackson3 {
    tools.jackson.databind.ObjectMapper forbidden;
  }

  @org.springframework.stereotype.Service
  public static class Spring {}
}
