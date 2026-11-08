package by.moby.common.events;

import by.moby.common.money.Money;

import java.time.Instant;
import java.util.UUID;

/** Кэшбэк от артефакта маскота зачислен на счёт ребёнка. */
public record CashbackGranted(
        UUID eventId,
        Instant occurredAt,
        String familyId,
        String childId,
        String operationId,
        String artifactId,
        String artifactName,
        int percent,
        Money amount) implements IntegrationEvent {
}
