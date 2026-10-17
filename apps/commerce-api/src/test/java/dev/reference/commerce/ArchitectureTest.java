package dev.reference.commerce;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.*;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class ArchitectureTest {
  @Test
  void dependencyBoundaries() {
    var classes = new ClassFileImporter().importPackages("dev.reference.commerce");
    noClasses()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "..infrastructure..")
        .check(classes);
    for (String module : java.util.List.of("orders", "inventory", "returns")) {
      noClasses()
          .that()
          .resideOutsideOfPackage(".." + module + ".infrastructure..")
          .and()
          .resideOutsideOfPackage("..shared.infrastructure..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage(".." + module + ".infrastructure..")
          .check(classes);
    }
    noClasses()
        .that()
        .resideInAPackage("..application..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..infrastructure..")
        .check(classes);
  }

  @Test
  void ruleDetectsForbiddenDependency() {
    var classes =
        new ClassFileImporter()
            .importClasses(ForbiddenDomain.class, org.springframework.jdbc.core.JdbcTemplate.class);
    ArchRule boundary =
        noClasses()
            .that()
            .haveSimpleName("ForbiddenDomain")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("org.springframework..");
    assertThatThrownBy(() -> boundary.check(classes)).isInstanceOf(AssertionError.class);
  }

  static class ForbiddenDomain {
    org.springframework.jdbc.core.JdbcTemplate forbidden;
  }
}
