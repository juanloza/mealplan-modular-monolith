package com.example.mealplan.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;
import jakarta.persistence.Entity;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import org.springframework.data.repository.Repository;
import org.springframework.web.bind.annotation.RestController;

/**
 * The rules this application claims to follow, checked by a tool rather than by trust.
 *
 * <p>There are eighteen and each one is its own constant. Grouping two of them would save a few
 * lines and make it impossible to prove them broken one at a time, which is the only way of knowing
 * that a rule actually detects anything.
 */
@AnalyzeClasses(packages = ArchitectureTest.ROOT,
                importOptions = {ImportOption.DoNotIncludeTests.class, ImportOption.DoNotIncludeJars.class})
class ArchitectureTest {

    static final String ROOT = "com.example.mealplan";

    /** The four business modules. {@code shared} is not one of them: everybody may depend on it. */
    private static final String[] MODULES = {"iam", "catalog", "pantry", "planning"};

    private static final String[] LAYERS = {"domain", "application", "infrastructure", "web", "config"};

    // 1. A module is reachable only through its api package.

    @ArchTest
    static final ArchRule modulesAreReachedOnlyThroughTheirApi = compose(
            module -> noClasses()
                    .that().resideOutsideOfPackage(ROOT + "." + module + "..")
                    .should().dependOnClassesThat().resideInAnyPackage(internalPackagesOf(module))
                    .because("a module may only see the api package of another, never its internals"));

    // 2. A public contract does not drag the internals of a business module behind it.

    @ArchTest
    static final ArchRule publicContractsDoNotExposeInternals = compose(
            module -> noClasses()
                    .that().resideInAPackage("..api..")
                    .should().dependOnClassesThat().resideInAnyPackage(internalPackagesOf(module))
                    .because("what one module publishes has to be usable without its internals"));

    // 3. No Spring inside the domain. The persistence annotations are a conscious exception and
    // live on the entities; anything from org.springframework is not.

    @ArchTest
    static final ArchRule domainKnowsNothingOfSpring = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework..")
            .because("the domain has to be testable, and readable, without a framework");

    // 4. What is shared does not depend on what is specific.

    @ArchTest
    static final ArchRule sharedDependsOnNoModule = noClasses()
            .that().resideInAPackage(ROOT + ".shared..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    Arrays.stream(MODULES).map(module -> ROOT + "." + module + "..").toArray(String[]::new))
            .because("shared code that knows a module is no longer shared");

    // 5. No cycles between modules.

    @ArchTest
    static final ArchRule modulesAreFreeOfCycles = slices()
            .matching(ROOT + ".(*)..")
            .should().beFreeOfCycles();

    // 6 to 8. Every kind of class in its own layer.

    @ArchTest
    static final ArchRule entitiesLiveInTheDomain = classes()
            .that().areAnnotatedWith(Entity.class)
            .should().resideInAPackage("..domain..");

    @ArchTest
    static final ArchRule controllersLiveInTheWebLayer = classes()
            .that().areAnnotatedWith(RestController.class)
            .should().resideInAPackage("..web..");

    @ArchTest
    static final ArchRule repositoriesLiveInInfrastructure = classes()
            .that().areAssignableTo(Repository.class)
            .should().resideInAPackage("..infrastructure..");

    // 9 to 12. The direction of the dependencies inside a module.

    @ArchTest
    static final ArchRule webDoesNotReachIntoInfrastructure = noClasses()
            .that().resideInAPackage("..web..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
            .because("a controller that queries the repository skips the rule the service holds");

    @ArchTest
    static final ArchRule applicationKnowsNothingOfHttp = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..web..");

    @ArchTest
    static final ArchRule domainLooksAtNothingAboveIt = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..application..", "..infrastructure..", "..web..");

    @ArchTest
    static final ArchRule infrastructureKnowsNothingOfItsCallers = noClasses()
            .that().resideInAPackage("..infrastructure..")
            .should().dependOnClassesThat().resideInAnyPackage("..application..", "..web..");

    // 13. Constructor injection everywhere.

    @ArchTest
    static final ArchRule noFieldInjection = GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

    // 14. Authorisation by ownership lives in the application service, inside the query that loads
    // the aggregate, and never in an expression the compiler cannot check.

    @ArchTest
    static final ArchRule noMethodLevelSecurityAnnotations = CompositeArchRule
            .of(noClasses().should().beAnnotatedWith("org.springframework.security.access.prepost.PreAuthorize"))
            .and(noClasses().should().beAnnotatedWith("org.springframework.security.access.prepost.PostAuthorize"))
            .and(noClasses().should().beAnnotatedWith("org.springframework.security.access.annotation.Secured"))
            .and(noClasses().should().beAnnotatedWith("jakarta.annotation.security.RolesAllowed"))
            .and(noMethods().should().beAnnotatedWith("org.springframework.security.access.prepost.PreAuthorize"))
            .and(noMethods().should().beAnnotatedWith("org.springframework.security.access.prepost.PostAuthorize"))
            .and(noMethods().should().beAnnotatedWith("org.springframework.security.access.annotation.Secured"))
            .and(noMethods().should().beAnnotatedWith("jakarta.annotation.security.RolesAllowed"))
            .because("ownership needs the aggregate loaded, so it is decided where it is loaded");

    // 15. The time arrives through the injected clock, always.

    @ArchTest
    static final ArchRule timeComesOnlyFromTheClock = noClasses()
            .that().resideOutsideOfPackage(ROOT + ".shared.config..")
            .should().callMethod(Instant.class, "now")
            .orShould().callMethod(LocalDate.class, "now")
            .orShould().callMethod(LocalDateTime.class, "now")
            .orShould().callMethod(System.class, "currentTimeMillis")
            .orShould().callConstructor(java.util.Date.class)
            .because("a test with a fixed clock must not see some components in one instant and the rest in another");

    // 16. The old date API is not used at all. The library rule covers java.util.Date and
    // java.util.Calendar, which are the two this project bans, and a handful more of the same era
    // that nobody wants either.

    @ArchTest
    static final ArchRule noLegacyDateApi = GeneralCodingRules.OLD_DATE_AND_TIME_CLASSES_SHOULD_NOT_BE_USED;

    // 17 and 18.

    @ArchTest
    static final ArchRule noStandardStreams = GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

    @ArchTest
    static final ArchRule noGenericExceptions = GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;

    /**
     * The two module rules say the same thing about four modules each. They are still one rule, so
     * they are one constant: what has to be provable one at a time is the rule, not the module.
     */
    private static CompositeArchRule compose(java.util.function.Function<String, ArchRule> perModule) {
        CompositeArchRule composite = CompositeArchRule.of(perModule.apply(MODULES[0]));
        for (int i = 1; i < MODULES.length; i++) {
            composite = composite.and(perModule.apply(MODULES[i]));
        }
        return composite;
    }

    /**
     * Everything of a module except its api package. Written by enumerating the four business
     * modules and not as {@code ..domain..}: that wildcard would also catch {@code shared.domain},
     * where {@code Quantity} and {@code UserId} live, and those do appear in every public contract.
     */
    private static String[] internalPackagesOf(String module) {
        return Arrays.stream(LAYERS)
                .map(layer -> ROOT + "." + module + "." + layer + "..")
                .toArray(String[]::new);
    }
}
