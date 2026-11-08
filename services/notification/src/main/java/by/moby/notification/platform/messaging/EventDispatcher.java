package by.moby.notification.platform.messaging;

import by.moby.common.events.IntegrationEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Единая точка входа событий из Kafka. Обработка и отметка в {@code processed_events} — одна транзакция:
 * либо событие обработано и отмечено, либо ни то ни другое (и Kafka доставит его снова).
 */
@Component
class EventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EventDispatcher.class);

    private final Map<Class<?>, List<EventHandler<?>>> handlers;
    private final IntegrationEventReader reader;
    private final ProcessedEvents processed;
    private final TransactionTemplate tx;
    private final String consumer;

    EventDispatcher(List<EventHandler<?>> handlers, IntegrationEventReader reader, ProcessedEvents processed, TransactionTemplate tx,
                    @Value("${spring.application.name}") String consumer) {
        this.handlers = handlers.stream().collect(Collectors.groupingBy(EventHandler::type));
        this.reader = reader;
        this.processed = processed;
        this.tx = tx;
        this.consumer = consumer;
    }

    @KafkaListener(topics = "#{'${moby.kafka.topics}'.split(',')}", groupId = "${spring.application.name}")
    void onMessage(ConsumerRecord<String, byte[]> record) {
        Optional<IntegrationEvent> event = reader.read(record);
        event.ifPresent(this::dispatch);
    }

    /** Также вызывается напрямую в тестах обработчиков. */
    void dispatch(IntegrationEvent event) {
        List<EventHandler<?>> forType = handlers.getOrDefault(event.getClass(), List.of());
        if (forType.isEmpty()) {
            return;
        }
        tx.executeWithoutResult(status -> {
            if (!processed.markProcessed(event.eventId(), consumer)) {
                log.debug("duplicate {} {} skipped", event.getClass().getSimpleName(), event.eventId());
                return;
            }
            forType.forEach(h -> handle(h, event));
        });
    }

    @SuppressWarnings("unchecked")
    private static <E extends IntegrationEvent> void handle(EventHandler<E> handler, IntegrationEvent event) {
        handler.handle((E) event);
    }
}
