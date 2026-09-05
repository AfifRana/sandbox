package com.example.payment.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class HexagonalArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter()
            .importPackages("com.example.payment");

    @Test
    void domainDoesNotDependOnOutsideLayers() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..application..", "..adapter..", "org.springframework..")
                .check(classes);
    }

    @Test
    void applicationDoesNotDependOnAdapters() {
        noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat()
                .resideInAPackage("..adapter..")
                .check(classes);
    }

    @Test
    void strategiesDoNotDependOnAdapters() {
        noClasses().that().resideInAPackage("..application.strategy..")
                .should().dependOnClassesThat()
                .resideInAPackage("..adapter..")
                .check(classes);
    }
}
