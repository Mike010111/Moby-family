package by.moby.common.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Событие, которое уходит между сервисами через transactional outbox → Kafka.
 * {@link #eventId()} — ключ идемпотентности консьюмеров, {@link #childId()} — ключ партиционирования.
 */
public interface IntegrationEvent {

    UUID eventId();

    Instant occurredAt();

    String familyId();

    String childId();
}
