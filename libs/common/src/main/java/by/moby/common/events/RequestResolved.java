package by.moby.common.events;

import by.moby.common.money.Money;

import java.time.Instant;
import java.util.UUID;

/** Главный родственник одобрил или отклонил запрос. {@code returned} — сколько вернулось из копилки. */
public record RequestResolved(
        UUID eventId,
        Instant occurredAt,
        String familyId,
        String childId,
        String requestId,
        RequestKind kind,
        boolean approved,
        Money amount,
        String reason,
        Actor resolver,
        String piggyGoal,
        Money returned) implements IntegrationEvent {
}
