package by.moby.rewards.platform.messaging;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Журнал обработанных событий: повторная доставка (at-least-once Kafka) не меняет состояние второй раз. */
@Component
class ProcessedEvents {

    private final JdbcClient jdbc;
    private final Clock clock;

    ProcessedEvents(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** true — событие видим впервые (запись добавлена в текущей транзакции). */
    @Transactional(propagation = Propagation.MANDATORY)
    boolean markProcessed(UUID eventId, String consumer) {
        return jdbc.sql("insert into processed_events (event_id, consumer, processed_at) values (:id, :consumer, :at) on conflict do nothing")
                .param("id", eventId)
                .param("consumer", consumer)
                .param("at", Timestamp.from(Instant.now(clock)))
                .update() == 1;
    }
}
