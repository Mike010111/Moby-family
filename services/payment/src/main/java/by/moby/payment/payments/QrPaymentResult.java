package by.moby.payment.payments;

import by.moby.common.error.ErrorCode;
import by.moby.common.money.Money;
import by.moby.payment.catalog.Merchant;

/**
 * Итог оплаты по QR. Сохраняется как ответ для повторов с тем же Idempotency-Key —
 * в том числе отказ: отклонённая оплата тоже записана в выписку.
 */
public record QrPaymentResult(
        Status status,
        String operationId,
        Merchant merchant,
        Integer earned,
        String legal,
        Cashback cashback,
        Money balance,
        ErrorCode code,
        String reason) {

    public enum Status { COMPLETED, DECLINED }

    public record Cashback(Money value, int pct, String artifact) {
    }
}
