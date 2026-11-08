package by.moby.payment;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** Границы модулей Spring Modulith внутри сервиса: нет циклов и обращений к внутренним пакетам. */
class ModularityTest {

    @Test
    void modulesRespectBoundaries() {
        ApplicationModules.of(PaymentApplication.class).verify();
    }
}
