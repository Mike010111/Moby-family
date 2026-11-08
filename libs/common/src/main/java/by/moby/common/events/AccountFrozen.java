package by.moby.common.events;

import java.time.Instant;
import java.util.UUID;

/** Родитель заблокировал счёт ребёнка: операции по деньгам остановлены. */
public record AccountFrozen(
        UUID eventId,
        Instant occurredAt,
        String familyId,
        String childId,
        String childName,
        String cardTitle,
        String cardMask,
        Actor actor) implements IntegrationEvent {
}
