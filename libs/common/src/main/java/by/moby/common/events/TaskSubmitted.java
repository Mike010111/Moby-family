package by.moby.common.events;

import by.moby.common.money.Money;

import java.time.Instant;
import java.util.UUID;

/** Ребёнок отметил задание выполненным — ждёт проверки родителем. */
public record TaskSubmitted(
        UUID eventId,
        Instant occurredAt,
        String familyId,
        String childId,
        String childName,
        String taskId,
        String title,
        Money reward) implements IntegrationEvent {
}
