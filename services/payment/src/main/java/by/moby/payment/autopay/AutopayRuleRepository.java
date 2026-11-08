package by.moby.payment.autopay;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AutopayRuleRepository extends JpaRepository<AutopayRule, String> {

    List<AutopayRule> findByEnabledTrue();
}
