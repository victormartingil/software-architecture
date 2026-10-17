package dev.reference.commerce;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import java.util.ArrayList;
import java.util.List;

/** One rule set for production checks and executable counterexamples. */
final class ArchitectureRules {
  static final String ROOT = "dev.reference.commerce";
  static final ArchRule DOMAIN =
      classes()
          .that()
          .resideInAPackage(ROOT + "..domain..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage("java..", ROOT + "..domain..");
  static final ArchRule APPLICATION =
      noClasses()
          .that()
          .resideInAPackage(ROOT + "..application..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage(ROOT + "..infrastructure..");

  static final SliceAssignment BUSINESS_MODULES = new SliceAssignment() {
    public SliceIdentifier getIdentifierOf(JavaClass type) {
      for (String module : List.of("orders", "inventory", "returns")) {
        if (type.getPackageName().startsWith(ROOT + "." + module + ".")) return SliceIdentifier.of(module);
      }
      return SliceIdentifier.ignore();
    }
    public String getDescription() { return "business modules excluding shared and composition"; }
  };
  static final ArchRule MODULE_CYCLES = slices().assignedFrom(BUSINESS_MODULES).should().beFreeOfCycles();

  static final List<ArchRule> ALL = rules();

  private static List<ArchRule> rules() {
    var rules = new ArrayList<ArchRule>(List.of(DOMAIN, APPLICATION, MODULE_CYCLES));
    rules.add(
        classes()
            .that()
            .areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
            .should()
            .resideInAnyPackage(
                ROOT + ".orders.infrastructure.rest",
                ROOT + ".inventory.infrastructure.rest",
                ROOT + ".returns.infrastructure.rest"));
    rules.add(noClasses().should().resideInAPackage(ROOT + "..infrastructure"));
    for (String module : List.of("orders", "inventory", "returns")) {
      rules.add(
          noClasses()
              .that()
              .resideOutsideOfPackage(ROOT + "." + module + "..")
              .should()
              .dependOnClassesThat()
              .resideInAPackage(ROOT + "." + module + ".application.port.output.."));
      rules.add(
          noClasses()
              .that()
              .resideOutsideOfPackage(ROOT + "." + module + ".infrastructure..")
              .and()
              .resideOutsideOfPackage(ROOT + ".shared.infrastructure..")
              .should()
              .dependOnClassesThat()
              .resideInAPackage(ROOT + "." + module + ".infrastructure.."));
    }
    return List.copyOf(rules);
  }

  private ArchitectureRules() {}
}
