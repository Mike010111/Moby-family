package by.moby.payment.account;

import by.moby.common.money.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Единственная точка изменения балансов. Каждая операция — проводка с нулевой суммой записей.
 * Клиентские счета должны быть заблокированы вызывающей стороной ({@link ChildAccounts#lock}).
 * У технических счетов (SYSTEM) баланс не кэшируется — он равен Σ проводок; поэтому их строки
 * не блокируются и не становятся «горячей точкой» для параллельных платежей.
 */
@Service
public class Ledger {

    private final LedgerTransactionRepository transactions;
    private final Clock clock;

    public Ledger(LedgerTransactionRepository transactions, Clock clock) {
        this.transactions = transactions;
        this.clock = clock;
    }

    public record Posting(Account account, Money amount) {
    }

    /** Перевод {@code amount} со счёта {@code from} на счёт {@code to}. */
    @Transactional(propagation = Propagation.MANDATORY)
    public LedgerTransaction transfer(LedgerKind kind, String description, Account from, Account to, Money amount) {
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Transfer amount must be positive: " + amount);
        }
        return post(kind, description, List.of(new Posting(from, amount.negate()), new Posting(to, amount)));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public LedgerTransaction post(LedgerKind kind, String description, List<Posting> postings) {
        Money total = postings.stream().map(Posting::amount).reduce(Money.ZERO, Money::plus);
        if (!total.isZero() || postings.size() < 2) {
            throw new IllegalArgumentException("Unbalanced ledger transaction: " + postings);
        }
        LedgerTransaction tx = new LedgerTransaction(kind, description, Instant.now(clock));
        for (Posting p : postings) {
            if (p.account().getKind() != AccountKind.SYSTEM) {
                p.account().apply(p.amount());
            }
            tx.add(new LedgerEntry(tx, p.account().getId(), p.amount()));
        }
        return transactions.save(tx);
    }
}
