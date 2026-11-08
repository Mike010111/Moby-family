package by.moby.payment.overview;

import by.moby.payment.account.Account;
import by.moby.payment.account.AccountOperation;
import by.moby.payment.account.CardDetails;
import by.moby.payment.autopay.AutopayRule;
import by.moby.payment.limits.api.LimitView;
import by.moby.payment.p2p.P2pApproval;
import by.moby.payment.subscriptions.Subscription;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper
interface OverviewMapper {

    @Mapping(target = "childId", source = "account.ownerId")
    @Mapping(target = "familyId", source = "account.familyId")
    @Mapping(target = "name", expression = "java(account.getHolder().name())")
    @Mapping(target = "cohort", source = "account.cohort")
    @Mapping(target = "balance", source = "account.balance")
    @Mapping(target = "frozen", source = "account.frozen")
    @Mapping(target = "card", source = "account.card")
    @Mapping(target = "allow", source = "account.permissions")
    @Mapping(target = "limits", source = "limits")
    @Mapping(target = "autopay", source = "autopay")
    @Mapping(target = "subscriptions", source = "subscriptions")
    @Mapping(target = "approvals", source = "approvals")
    @Mapping(target = "stats", expression = "java(stats(account))")
    ChildAccountView toView(Account account, List<LimitView> limits, AutopayRule autopay,
                            List<Subscription> subscriptions, List<P2pApproval> approvals);

    ChildAccountView.Card card(CardDetails card);

    @Mapping(target = "every", expression = "java(rule.getEvery().key())")
    ChildAccountView.Autopay autopay(AutopayRule rule);

    @Mapping(target = "next", source = "nextLabel")
    ChildAccountView.SubscriptionItem subscription(Subscription subscription);

    @Mapping(target = "max", source = "maxAmount")
    ChildAccountView.Approval approval(P2pApproval approval);

    @Mapping(target = "ts", source = "createdAt")
    OperationView operation(AccountOperation operation);

    List<OperationView> operations(List<AccountOperation> operations);

    @Mapping(target = "memberId", source = "ownerId")
    @Mapping(target = "name", expression = "java(account.getHolder().name())")
    @Mapping(target = "cardMask", source = "card.mask")
    ParentAccountView parent(Account account);

    default ChildAccountView.Stats stats(Account account) {
        return account.getStatsTxCount() == null ? null : new ChildAccountView.Stats(account.getStatsTxCount(), account.getStatsSince());
    }
}
