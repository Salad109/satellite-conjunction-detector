package io.salad109.conjunctionapi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.modulith.core.ApplicationModules;

@SpringBootTest
class ConjunctionApiApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void verifiesModularStructure() {
        ApplicationModules modules = ApplicationModules.of(ConjunctionApiApplication.class);
        modules.verify();
    }

}
