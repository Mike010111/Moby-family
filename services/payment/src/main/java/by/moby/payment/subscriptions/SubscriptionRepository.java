package by.moby.payment.subscriptions;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, String> {

    List<Subscription> findByAccountIdOrderByPositionAsc(String accountId);

    Optional<Subscription> findByIdAndAccountId(String id, String accountId);
}
