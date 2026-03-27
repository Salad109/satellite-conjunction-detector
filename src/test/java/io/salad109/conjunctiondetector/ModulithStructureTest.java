package io.salad109.conjunctiondetector;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModulithStructureTest {

    @Test
    void verifiesModularStructure() {
        ApplicationModules modules = ApplicationModules.of(ConjunctionDetectorApplication.class);
        modules.verify();
    }

    @Test
    void writeDocumentationSnippets() {
        ApplicationModules modules = ApplicationModules.of(ConjunctionDetectorApplication.class);
        new Documenter(modules)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml();
    }

}
