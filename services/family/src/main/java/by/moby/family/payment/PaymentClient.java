package by.moby.family.payment;

import by.moby.common.error.ErrorCode;
import by.moby.common.money.Money;

/**
 * Деньги family двигает только через API payment. Отказы payment приходят как
 * {@link by.moby.common.error.BusinessException} с кодом и текстом payment.
 */
public interface PaymentClient {

    enum TransferKind { TOPUP, REWARD }

    record TransferResult(String transactionId, Money amount, Money childBalance, Money counterpartBalance) {
    }

    record SavingsResult(String piggyId, Money amount, Money saved, Money childBalance) {
    }

    record Quote(String mode, boolean ok, ErrorCode code, String reason, Money remaining, String limitName,
                 Money approvalMax, Money balance, String contactName) {
    }

    TransferResult parentToChild(String childId, Money amount, TransferKind kind, String title, String idempotencyKey);

    void grantP2pApproval(String childId, String requestId, Money max);

    void openSavings(String childId, String piggyId);

    SavingsResult depositToSavings(String piggyId, Money amount, String title, String idempotencyKey);

    SavingsResult withdrawSavings(String piggyId, String title, String idempotencyKey);

    Quote quoteP2p(String childId, String amount, String contactId);
}
