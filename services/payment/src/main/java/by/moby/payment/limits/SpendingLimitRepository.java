package by.moby.payment.limits;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpendingLimitRepository extends JpaRepository<SpendingLimit, String> {

    List<SpendingLimit> findByAccountIdOrderByStartAtAscIdAsc(String accountId);

    Optional<SpendingLimit> findByIdAndAccountId(String id, String accountId);

    void deleteByAccountId(String accountId);
}
