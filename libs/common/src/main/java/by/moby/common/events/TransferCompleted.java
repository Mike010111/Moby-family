package by.moby.common.events;

import by.moby.common.money.Money;

import java.time.Instant;
import java.util.UUID;

/** Движение денег между счетами (пополнение, автоперевод, награда, P2P, копилка). */
public record TransferCompleted(
        UUID eventId,
        Instant occurredAt,
        String familyId,
        String childId,
        String transactionId,
        Kind kind,
        Money amount,
        String title) implements IntegrationEvent {

    public enum Kind { TOPUP, AUTOPAY, REWARD, P2P, SAVINGS_DEPOSIT, SAVINGS_WITHDRAW }
}
