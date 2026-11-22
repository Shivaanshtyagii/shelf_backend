package com.shelf;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ModuleArchitectureTest {
    @Test
    void modulesOnlyUsePublicInterfacesAndHaveNoCycles() {
        ApplicationModules.of(ShelfApplication.class).verify();
    }

    @Test
    void controllersAndRepositoriesRespectLayerBoundaries() {
        var code = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.shelf");

        noClasses().that().resideOutsideOfPackage("..internal.persistence..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.jdbc..", "java.sql..")
                .check(code);
        noClasses().that().resideOutsideOfPackage("..assets.internal.integration..")
                .should().dependOnClassesThat().resideInAPackage("software.amazon.awssdk..")
                .check(code);
        noClasses().that().resideInAPackage("..internal.controller..")
                .should().dependOnClassesThat().resideInAPackage("..internal.persistence..")
                .check(code);
        noClasses().that().resideInAPackage("..internal.persistence..")
                .should().dependOnClassesThat().resideInAnyPackage("..internal.controller..", "..internal.service..")
                .check(code);
    }
}
