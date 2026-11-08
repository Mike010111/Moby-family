package by.moby.common.events;

import by.moby.common.money.Money;

import java.time.Instant;
import java.util.UUID;

/** Ребёнок пополнил копилку: начисляются {@code points} баллов. */
public record PiggyDeposited(
        UUID eventId,
        Instant occurredAt,
        String familyId,
        String childId,
        String piggyId,
        Money amount,
        int points) implements IntegrationEvent {
}
