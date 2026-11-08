package by.moby.payment.overview;

import by.moby.payment.account.Account;
import by.moby.payment.account.AccountOperationRepository;
import by.moby.payment.account.ChildAccounts;
import by.moby.payment.autopay.AutopayService;
import by.moby.payment.limits.LimitService;
import by.moby.payment.limits.api.LimitViewMapper;
import by.moby.payment.p2p.P2pApprovalRepository;
import by.moby.payment.platform.security.AccessPolicy;
import by.moby.payment.platform.security.CurrentUser;
import by.moby.payment.subscriptions.SubscriptionService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Limit;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/payment")
class OverviewController {

    private final ChildAccounts accounts;
    private final AccessPolicy access;
    private final LimitService limits;
    private final LimitViewMapper limitMapper;
    private final AutopayService autopay;
    private final SubscriptionService subscriptions;
    private final P2pApprovalRepository approvals;
    private final AccountOperationRepository operations;
    private final OverviewMapper mapper;

    OverviewController(ChildAccounts accounts, AccessPolicy access, LimitService limits, LimitViewMapper limitMapper,
                       AutopayService autopay, SubscriptionService subscriptions, P2pApprovalRepository approvals,
                       AccountOperationRepository operations, OverviewMapper mapper) {
        this.accounts = accounts;
        this.access = access;
        this.limits = limits;
        this.limitMapper = limitMapper;
        this.autopay = autopay;
        this.subscriptions = subscriptions;
        this.approvals = approvals;
        this.operations = operations;
        this.mapper = mapper;
    }

    @GetMapping("/children/{childId}/account")
    @Transactional(readOnly = true)
    ChildAccountView account(@PathVariable String childId) {
        Account child = accounts.readableChild(childId);
        return mapper.toView(child, limitMapper.toViews(limits.states(child)), autopay.of(child).orElse(null),
                subscriptions.of(child), approvals.findByAccountIdOrderByCreatedAtDesc(child.getId()));
    }

    /** Выписка (последние операции, новые сверху) — {@code child.tx} прототипа. */
    @GetMapping("/children/{childId}/operations")
    @Transactional(readOnly = true)
    List<OperationView> operations(@PathVariable String childId, @RequestParam(defaultValue = "60") @Min(1) @Max(200) int limit) {
        Account child = accounts.readableChild(childId);
        return mapper.operations(operations.findByAccountIdOrderByCreatedAtDesc(child.getId(), Limit.of(limit)));
    }

    @GetMapping("/me/parent-account")
    @Transactional(readOnly = true)
    ParentAccountView parentAccount() {
        CurrentUser user = access.current();
        access.requireParentOf(user.familyId());
        return mapper.parent(accounts.parentOfFamily(user.familyId()));
    }
}
