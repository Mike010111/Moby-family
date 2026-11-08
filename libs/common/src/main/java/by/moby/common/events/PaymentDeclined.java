package by.moby.common.events;

import by.moby.common.error.ErrorCode;
import by.moby.common.money.Money;

import java.time.Instant;
import java.util.UUID;

/** Отклонённая оплата: причина — код проверки {@code checkPayment}. */
public record PaymentDeclined(
        UUID eventId,
        Instant occurredAt,
        String familyId,
        String childId,
        String childName,
        String operationId,
        String merchantName,
        Money amount,
        String mcc,
        ErrorCode code,
        String reason) implements IntegrationEvent {
}
