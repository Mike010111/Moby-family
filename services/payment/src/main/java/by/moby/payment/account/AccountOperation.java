package by.moby.payment.account;

import by.moby.common.error.ErrorCode;
import by.moby.common.money.Money;
import by.moby.payment.catalog.Categories;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Строка выписки ребёнка (элемент {@code child.tx} в прототипе). Успешная операция ссылается на
 * проводку ledger; отклонённая — нет. По этим строкам считаются траты для лимитов.
 */
@Entity
@Table(name = "account_operations")
public class AccountOperation {

    @Id
    private String id;
    private String accountId;
    private String title;
    private Money amount;
    private String mcc;
    private String category;
    private boolean online;
    @Enumerated(EnumType.STRING)
    private OperationStatus status;
    @Enumerated(EnumType.STRING)
    private ErrorCode declineCode;
    private String note;
    private Instant createdAt;
    private UUID ledgerTransactionId;

    protected AccountOperation() {
    }

    private AccountOperation(String accountId, String title, Money amount, String mcc, boolean online, Instant createdAt) {
        this.id = UUID.randomUUID().toString();
        this.accountId = accountId;
        this.title = title;
        this.amount = amount;
        this.mcc = mcc;
        this.category = Categories.groupOf(mcc);
        this.online = online;
        this.createdAt = createdAt;
    }

    public static AccountOperation completed(String accountId, String title, Money amount, String mcc, boolean online,
                                             String note, Instant at, LedgerTransaction ledgerTx) {
        AccountOperation op = new AccountOperation(accountId, title, amount, mcc, online, at);
        op.status = OperationStatus.COMPLETED;
        op.note = note;
        op.ledgerTransactionId = ledgerTx == null ? null : ledgerTx.getId();
        return op;
    }

    public static AccountOperation declined(String accountId, String title, Money amount, String mcc, ErrorCode code, String reason, Instant at) {
        AccountOperation op = new AccountOperation(accountId, title, amount, mcc, false, at);
        op.status = OperationStatus.DECLINED;
        op.declineCode = code;
        op.note = reason;
        return op;
    }

    /** Историческая строка демо-выписки (до открытия счёта в системе — без проводки). */
    public static AccountOperation history(String id, String accountId, String title, Money amount, String mcc, boolean online, Instant at) {
        AccountOperation op = new AccountOperation(accountId, title, amount, mcc, online, at);
        op.id = id;
        op.status = OperationStatus.COMPLETED;
        return op;
    }

    public boolean isSpend() {
        return status == OperationStatus.COMPLETED && amount.isNegative();
    }

    public String getId() {
        return id;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getTitle() {
        return title;
    }

    public Money getAmount() {
        return amount;
    }

    public String getMcc() {
        return mcc;
    }

    public String getCategory() {
        return category;
    }

    public boolean isOnline() {
        return online;
    }

    public OperationStatus getStatus() {
        return status;
    }

    public ErrorCode getDeclineCode() {
        return declineCode;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getLedgerTransactionId() {
        return ledgerTransactionId;
    }
}
