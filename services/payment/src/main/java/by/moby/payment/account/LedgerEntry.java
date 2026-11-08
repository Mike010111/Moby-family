package by.moby.payment.account;

import by.moby.common.money.Money;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Запись проводки: положительная сумма увеличивает баланс счёта, отрицательная — уменьшает. */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private LedgerTransaction transaction;
    private String accountId;
    private Money amount;

    protected LedgerEntry() {
    }

    LedgerEntry(LedgerTransaction transaction, String accountId, Money amount) {
        this.transaction = transaction;
        this.accountId = accountId;
        this.amount = amount;
    }

    public String getAccountId() {
        return accountId;
    }

    public Money getAmount() {
        return amount;
    }
}
