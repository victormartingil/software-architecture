package dev.reference.commerce;

import static org.assertj.core.api.Assertions.*;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import dev.reference.commerce.orders.domain.DomainBoundaryFixtures;
import dev.reference.commerce.returns.application.service.ApplicationBoundaryFixtures;
import org.junit.jupiter.api.Test;

class ArchitectureTest {
  @Test
  void dependencyBoundaries() {
    var classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ArchitectureRules.ROOT);
    for (String module : java.util.List.of("orders", "inventory", "returns")) {
      assertThat(classes.stream()
              .filter(type -> type.getPackageName().startsWith(ArchitectureRules.ROOT + "." + module + "."))
              .count())
          .isPositive();
    }
    ArchitectureRules.ALL.forEach(rule -> rule.check(classes));
  }

  @Test
  void domainRejectsJackson2() {
    rejects(DomainBoundaryFixtures.Jackson2.class, "com.fasterxml.jackson.databind.ObjectMapper");
  }

  @Test
  void domainRejectsJackson3() {
    rejects(DomainBoundaryFixtures.Jackson3.class, "tools.jackson.databind.ObjectMapper");
  }

  @Test
  void domainRejectsSpring() {
    rejects(DomainBoundaryFixtures.Spring.class, "org.springframework.stereotype.Service");
  }

  @Test
  void applicationRejectsAdapter() {
    rejects(ApplicationBoundaryFixtures.Adapter.class, "JdbcReturnRepositoryAdapter");
  }

  @Test
  void moduleRejectsForeignPersistencePort() {
    rejects(ApplicationBoundaryFixtures.ForeignRepository.class, "OrderRepository");
  }

  @Test
  void moduleCanUseForeignInputPort() {
    var classes =
        new ClassFileImporter().importClasses(ApplicationBoundaryFixtures.InputPort.class);
    ArchitectureRules.ALL.forEach(rule -> rule.allowEmptyShould(true).check(classes));
  }

  @Test
  void rejectsFirstModuleCycle() {
    rejectsCycle(dev.reference.commerce.orders.application.port.input.ModuleCycleFixtures.First.class,
        dev.reference.commerce.returns.application.port.input.ModuleCycleFixtures.First.class);
  }

  @Test
  void rejectsSecondModuleCycle() {
    rejectsCycle(dev.reference.commerce.orders.application.port.input.ModuleCycleFixtures.Second.class,
        dev.reference.commerce.returns.application.port.input.ModuleCycleFixtures.Second.class);
  }

  @Test
  void acceptsAcyclicBusinessCollaborationAndSharedTypes() {
    var classes = new ClassFileImporter().importClasses(ApplicationBoundaryFixtures.InputPort.class,
        dev.reference.commerce.orders.application.port.input.OrderUseCase.class, ApplicationBoundaryFixtures.SharedType.class);
    ArchitectureRules.ALL.forEach(rule -> rule.allowEmptyShould(true).check(classes));
  }

  private static void rejectsCycle(Class<?> first, Class<?> second) {
    var classes = new ClassFileImporter().importClasses(first, second);
    var result = ArchitectureRules.MODULE_CYCLES.evaluate(classes);
    assertThat(result.hasViolation()).isTrue();
    assertThat(result.getFailureReport().toString())
        .contains("Cycle detected", "orders", "returns", first.getName(), second.getName());
    ArchitectureRules.ALL.stream().filter(rule -> rule != ArchitectureRules.MODULE_CYCLES)
        .forEach(rule -> rule.allowEmptyShould(true).check(classes));
  }

  private static void rejects(Class<?> fixture, String target) {
    var classes = new ClassFileImporter().importClasses(fixture);
    // Ignore unrelated empty selections, but require a real dependency violation.
    var violations =
        ArchitectureRules.ALL.stream()
            .map(rule -> rule.allowEmptyShould(true).evaluate(classes))
            .filter(result -> result.hasViolation())
            .flatMap(result -> result.getFailureReport().getDetails().stream())
            .toList();
    assertThat(violations)
        .anySatisfy(detail -> assertThat(detail).contains(fixture.getName(), target));
  }
}
