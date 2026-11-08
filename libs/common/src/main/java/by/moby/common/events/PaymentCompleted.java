package by.moby.common.events;

import by.moby.common.money.Money;

import java.time.Instant;
import java.util.UUID;

/** Успешная оплата ребёнка (QR). {@code cashback} — null, если кэшбэк не начислялся. */
public record PaymentCompleted(
        UUID eventId,
        Instant occurredAt,
        String familyId,
        String childId,
        String childName,
        boolean childFemale,
        String operationId,
        String merchantName,
        Money amount,
        String mcc,
        String category,
        String categoryLabel,
        Cashback cashback) implements IntegrationEvent {

    public record Cashback(int percent, Money amount, String artifactId, String artifactName) {
    }
}
