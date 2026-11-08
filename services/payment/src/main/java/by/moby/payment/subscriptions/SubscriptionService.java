package by.moby.payment.subscriptions;

import by.moby.common.error.BusinessException;
import by.moby.common.error.ErrorCode;
import by.moby.common.money.Money;
import by.moby.payment.account.Account;
import by.moby.payment.account.ChildAccounts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** {@code sub/toggle}, {@code sub/update}, {@code sub/delete}: при заблокированном счёте недоступны. */
@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptions;
    private final ChildAccounts accounts;

    public SubscriptionService(SubscriptionRepository subscriptions, ChildAccounts accounts) {
        this.subscriptions = subscriptions;
        this.accounts = accounts;
    }

    public List<Subscription> of(Account account) {
        return subscriptions.findByAccountIdOrderByPositionAsc(account.getId());
    }

    @Transactional
    public Subscription update(String childId, String id, Boolean active, String rawPrice) {
        Subscription s = editable(childId, id);
        if (rawPrice != null) {
            s.changePrice(Money.parse(rawPrice).filter(Money::isPositive)
                    .orElseThrow(() -> new BusinessException(ErrorCode.AMOUNT, "Укажите сумму — не больше двух знаков после запятой")));
        }
        if (active != null) {
            s.setActive(active);
        }
        return s;
    }

    @Transactional
    public void delete(String childId, String id) {
        subscriptions.delete(editable(childId, id));
    }

    private Subscription editable(String childId, String id) {
        Account child = accounts.readableChild(childId);
        if (child.isFrozen()) {
            throw new BusinessException(ErrorCode.FROZEN, "Счёт заблокирован");
        }
        return subscriptions.findByIdAndAccountId(id, child.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Подписка не найдена"));
    }
}
