package by.moby.family;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

    @Test
    void modulesRespectBoundaries() {
        ApplicationModules.of(FamilyApplication.class).verify();
    }
}
