package by.moby.notification.platform.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Ошибка обработки: повторы с экспоненциальной паузой (до ~30 с), затем сообщение пропускается с записью в лог.
 * Повтор безопасен — обработка идемпотентна.
 */
@Configuration(proxyBeanMethods = false)
class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Bean
    DefaultErrorHandler kafkaErrorHandler() {
        ExponentialBackOff backOff = new ExponentialBackOff(500, 2.0);
        backOff.setMaxElapsedTime(30_000);
        return new DefaultErrorHandler((record, e) -> log.error("event {}-{}@{} skipped after retries",
                record.topic(), record.partition(), record.offset(), e), backOff);
    }
}
