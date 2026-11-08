package by.moby.common.events;

import java.time.Instant;
import java.util.UUID;

/** Родитель разблокировал счёт ребёнка. */
public record AccountUnfrozen(
        UUID eventId,
        Instant occurredAt,
        String familyId,
        String childId,
        String childName,
        String cardTitle,
        String cardMask,
        Actor actor) implements IntegrationEvent {
}
