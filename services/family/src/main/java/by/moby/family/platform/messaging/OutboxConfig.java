package by.moby.family.platform.messaging;

import by.moby.common.events.IntegrationEvent;
import by.moby.common.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.RoutingTarget;

import java.util.Map;

/**
 * Transactional outbox: события {@link IntegrationEvent} публикуются через ApplicationEventPublisher внутри
 * бизнес-транзакции, Spring Modulith сохраняет их в {@code event_publication} в той же транзакции и после
 * коммита отправляет в Kafka. Ключ сообщения — {@code childId}: события по ребёнку упорядочены.
 */
@Configuration(proxyBeanMethods = false)
class OutboxConfig {

    static final String TOPIC = Topics.FAMILY;

    @Bean
    EventExternalizationConfiguration eventExternalization() {
        return EventExternalizationConfiguration.externalizing()
                .select(event -> event instanceof IntegrationEvent)
                .route(IntegrationEvent.class, e -> RoutingTarget.forTarget(TOPIC).andKey(e.childId()))
                .headers(IntegrationEvent.class, e -> Map.of(
                        EventHeaders.TYPE, e.getClass().getSimpleName(),
                        EventHeaders.ID, e.eventId().toString()))
                .build();
    }

    @Bean
    NewTopic eventsTopic() {
        return TopicBuilder.name(TOPIC).partitions(3).replicas(1).build();
    }
}
