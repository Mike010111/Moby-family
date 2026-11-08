package by.moby.family.members;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Random;
import java.util.random.RandomGenerator;

@Configuration(proxyBeanMethods = false)
class RandomConfig {

    /** {@code java.util.Random}: алгоритмы {@code jdk.random} есть не во всех JRE-образах. */
    @Bean
    RandomGenerator randomGenerator() {
        return new Random();
    }
}
