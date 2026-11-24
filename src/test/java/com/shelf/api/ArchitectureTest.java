package com.shelf.api;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {
    @Test
    void featuresShareOnlyTheirNamedInterfacesAndHaveNoCycles() {
        ApplicationModules.of(ShelfApiApplication.class).verify();
    }

    @Test
    void layersRespectFeatureBoundaries() {
        var code = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.shelf.api");

        for (var feature : ApplicationModules.of(ShelfApiApplication.class)) {
            String root = feature.getBasePackage().getName();
            if (root.equals("com.shelf.api.common")) continue;
            noClasses().that().resideOutsideOfPackage(root + "..")
                    .should().dependOnClassesThat(resideInAPackage(root + "..")
                            .and(not(resideInAPackage(root + ".api.."))))
                    .check(code);
            noClasses().that().resideInAPackage("..common..").should().dependOnClassesThat()
                    .resideInAPackage(root + "..").check(code);
        }
        noClasses().that().resideOutsideOfPackage("..repository..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.jdbc..", "java.sql..")
                .check(code);
        noClasses().that().resideOutsideOfPackage("..assets.integration..")
                .should().dependOnClassesThat().resideInAPackage("software.amazon.awssdk..").check(code);
        noClasses().that().resideInAPackage("..web..").should().dependOnClassesThat()
                .resideInAPackage("..repository..").check(code);
        noClasses().that().resideInAPackage("..repository..").should().dependOnClassesThat()
                .resideInAnyPackage("com.shelf.api.*.web..", "com.shelf.api.*.service..").check(code);
        noClasses().that().resideInAPackage("com.shelf.api.*.api..").should().dependOnClassesThat()
                .resideInAnyPackage("com.shelf.api.*.web..", "com.shelf.api.*.service..", "com.shelf.api.*.repository..",
                        "com.shelf.api.*.domain..", "com.shelf.api.*.integration..").check(code);
    }
}
