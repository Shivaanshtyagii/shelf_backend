package com.shelf;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModuleArchitectureTest {
    @Test
    void modulesOnlyUsePublicInterfacesAndHaveNoCycles() {
        ApplicationModules.of(ShelfApplication.class).verify();
    }
}
