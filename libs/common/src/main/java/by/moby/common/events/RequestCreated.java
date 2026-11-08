package by.moby.common.events;

import by.moby.common.money.Money;

import java.time.Instant;
import java.util.UUID;

/**
 * Ребёнок создал запрос. {@code toMain} — адресован главному родственнику (попадает в его ленту).
 * Поля {@code contactName}, {@code limitRemaining}, {@code piggy*} заполнены только для своих видов.
 */
public record RequestCreated(
        UUID eventId,
        Instant occurredAt,
        String familyId,
        String childId,
        String childName,
        String requestId,
        RequestKind kind,
        String relativeId,
        boolean toMain,
        Money amount,
        String reason,
        String contactName,
        Money limitRemaining,
        String piggyGoal,
        Money piggySaved,
        Money piggyTarget) implements IntegrationEvent {
}
