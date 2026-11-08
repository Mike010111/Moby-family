package by.moby.payment.account;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AccountOperationRepository extends JpaRepository<AccountOperation, String> {

    List<AccountOperation> findByAccountIdOrderByCreatedAtDesc(String accountId, Limit limit);

    /** Списания за период — для расчёта лимитов. Индекс {@code (account_id, created_at)}. */
    @Query("""
            select o from AccountOperation o
            where o.accountId = :accountId and o.status = by.moby.payment.account.OperationStatus.COMPLETED
              and o.amount < 0 and o.createdAt >= :from
            """)
    List<AccountOperation> findSpendsSince(@Param("accountId") String accountId, @Param("from") Instant from);
}
