package by.moby.common.events;

import by.moby.common.money.Money;

import java.time.Instant;
import java.util.UUID;

/** Цель копилки достигнута: собранные деньги вернулись на счёт ребёнка. */
public record PiggyCompleted(
        UUID eventId,
        Instant occurredAt,
        String familyId,
        String childId,
        String childName,
        String piggyId,
        String goal,
        Money amount) implements IntegrationEvent {
}
