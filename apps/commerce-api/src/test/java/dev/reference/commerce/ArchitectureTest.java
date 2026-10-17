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
    var classes = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages(ArchitectureRules.ROOT);
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
    var classes = new ClassFileImporter().importClasses(ApplicationBoundaryFixtures.InputPort.class);
    ArchitectureRules.ALL.forEach(rule -> rule.allowEmptyShould(true).check(classes));
  }

  private static void rejects(Class<?> fixture, String target) {
    var classes = new ClassFileImporter().importClasses(fixture);
    // Ignore unrelated empty selections, but require a real dependency violation.
    var violations = ArchitectureRules.ALL.stream()
        .map(rule -> rule.allowEmptyShould(true).evaluate(classes))
        .filter(result -> result.hasViolation())
        .flatMap(result -> result.getFailureReport().getDetails().stream()).toList();
    assertThat(violations).anySatisfy(detail ->
        assertThat(detail).contains(fixture.getName(), target));
  }
}
