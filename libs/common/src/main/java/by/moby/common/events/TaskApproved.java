package by.moby.common.events;

import by.moby.common.money.Money;

import java.time.Instant;
import java.util.UUID;

/** Родитель принял задание: награда выплачена, {@code points} баллов начисляются ребёнку. */
public record TaskApproved(
        UUID eventId,
        Instant occurredAt,
        String familyId,
        String childId,
        String taskId,
        String title,
        Money reward,
        int points) implements IntegrationEvent {
}
