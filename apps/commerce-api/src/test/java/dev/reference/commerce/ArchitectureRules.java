package dev.reference.commerce;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

import com.tngtech.archunit.lang.ArchRule;
import java.util.ArrayList;
import java.util.List;

/** One rule set for production checks and executable counterexamples. */
final class ArchitectureRules {
  static final String ROOT = "dev.reference.commerce";
  static final ArchRule DOMAIN = classes().that().resideInAPackage(ROOT + "..domain..")
      .should().onlyDependOnClassesThat()
      .resideInAnyPackage("java..", ROOT + "..domain..");
  static final ArchRule APPLICATION = noClasses().that()
      .resideInAPackage(ROOT + "..application..")
      .should().dependOnClassesThat().resideInAPackage(ROOT + "..infrastructure..");

  static final List<ArchRule> ALL = rules();

  private static List<ArchRule> rules() {
    var rules = new ArrayList<ArchRule>(List.of(DOMAIN, APPLICATION));
    for (String module : List.of("orders", "inventory", "returns")) {
      rules.add(noClasses().that()
          .resideOutsideOfPackage(ROOT + "." + module + "..")
          .should().dependOnClassesThat()
          .resideInAPackage(ROOT + "." + module + ".application.port.output.."));
      rules.add(noClasses().that()
          .resideOutsideOfPackage(ROOT + "." + module + ".infrastructure..")
          .and().resideOutsideOfPackage(ROOT + ".shared.infrastructure..")
          .should().dependOnClassesThat()
          .resideInAPackage(ROOT + "." + module + ".infrastructure.."));
    }
    return List.copyOf(rules);
  }

  private ArchitectureRules() {}
}
