package by.moby.payment.transfers;

import by.moby.common.money.Money;

/** Результат перевода: сумма и балансы после него. */
public record TransferResult(String transactionId, Money amount, Money childBalance, Money counterpartBalance) {
}
