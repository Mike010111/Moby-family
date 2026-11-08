package by.moby.payment.overview;

import by.moby.common.money.Money;

import java.time.Instant;

/** Строка выписки. {@code status}: COMPLETED | DECLINED. */
public record OperationView(String id, String title, Money amount, String mcc, String category, boolean online,
                            String status, String declineCode, String note, Instant ts) {
}
