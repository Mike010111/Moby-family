package by.moby.notification.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;

/** Kafka для интеграционных тестов outbox и консьюмеров. */
@TestConfiguration(proxyBeanMethods = false)
public class KafkaTestcontainersConfig {

    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        return new KafkaContainer("apache/kafka-native:4.3.2");
    }
}
