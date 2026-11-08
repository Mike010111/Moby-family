package by.moby.payment.account;

import by.moby.common.money.Money;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.UUID;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, UUID> {

    /** Σ проводок по счёту — для сверки с {@link Account#getBalance()}. */
    @Query("select coalesce(sum(e.amount), 0) from LedgerEntry e where e.accountId = :accountId")
    BigDecimal sumEntries(@Param("accountId") String accountId);

    /** Σ всех проводок — в двойной записи всегда 0. */
    @Query("select coalesce(sum(e.amount), 0) from LedgerEntry e")
    BigDecimal sumAllEntries();

    default Money balanceFromEntries(String accountId) {
        return Money.of(sumEntries(accountId));
    }
}
