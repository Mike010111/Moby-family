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
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Чтение: сводка счёта ребёнка, выписка и счёт родителя (read-модель для экранов). */
@Service
class AccountOverviewService {

    private final ChildAccounts accounts;
    private final AccessPolicy access;
    private final LimitService limits;
    private final LimitViewMapper limitMapper;
    private final AutopayService autopay;
    private final SubscriptionService subscriptions;
    private final P2pApprovalRepository approvals;
    private final AccountOperationRepository operations;
    private final OverviewMapper mapper;

    AccountOverviewService(ChildAccounts accounts, AccessPolicy access, LimitService limits, LimitViewMapper limitMapper,
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

    @Transactional(readOnly = true)
    ChildAccountView account(String childId) {
        Account child = accounts.readableChild(childId);
        return mapper.toView(child, limitMapper.toViews(limits.states(child)), autopay.of(child).orElse(null),
                subscriptions.of(child), approvals.findByAccountIdOrderByCreatedAtDesc(child.getId()));
    }

    @Transactional(readOnly = true)
    List<OperationView> operations(String childId, int limit) {
        Account child = accounts.readableChild(childId);
        return mapper.operations(operations.findByAccountIdOrderByCreatedAtDesc(child.getId(), Limit.of(limit)));
    }

    @Transactional(readOnly = true)
    ParentAccountView parentAccount() {
        CurrentUser user = access.current();
        access.requireParentOf(user.familyId());
        return mapper.parent(accounts.parentOfFamily(user.familyId()));
    }
}
