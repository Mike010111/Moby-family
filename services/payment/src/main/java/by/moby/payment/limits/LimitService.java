package by.moby.payment.limits;

import by.moby.common.error.BusinessException;
import by.moby.common.error.ErrorCode;
import by.moby.payment.account.Account;
import by.moby.payment.account.AccountOperation;
import by.moby.payment.account.AccountOperationRepository;
import by.moby.payment.account.ChildAccounts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class LimitService {

    private final SpendingLimitRepository limits;
    private final AccountOperationRepository operations;
    private final ChildAccounts accounts;
    private final Clock clock;

    public LimitService(SpendingLimitRepository limits, AccountOperationRepository operations, ChildAccounts accounts, Clock clock) {
        this.limits = limits;
        this.operations = operations;
        this.accounts = accounts;
        this.clock = clock;
    }

    /** Лимит с текущим состоянием — для витрины. */
    public record LimitState(SpendingLimit limit, LimitUsage usage, String title, String duration, String categoriesLabel) {
    }

    @Transactional
    public LimitState create(String childId, LimitValidator.Input input) {
        Account account = accounts.lock(accounts.managedChild(childId));
        Instant now = Instant.now(clock);
        LimitDraft draft = LimitValidator.validate(account.getCohort(), limitsOf(account), input, null, now, clock.getZone());
        SpendingLimit limit = limits.save(new SpendingLimit(null, account.getId(), draft, LimitPeriods.unitStart(now, draft.unit(), clock.getZone())));
        return state(account, limit);
    }

    @Transactional
    public LimitState update(String childId, String limitId, LimitValidator.Input input) {
        Account account = accounts.lock(accounts.managedChild(childId));
        SpendingLimit limit = limits.findByIdAndAccountId(limitId, account.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Лимит не найден"));
        Instant now = Instant.now(clock);
        LimitDraft draft = LimitValidator.validate(account.getCohort(), limitsOf(account), input, limitId, now, clock.getZone());
        if (limit.update(draft) || limit.getStartAt() == null) {
            limit.restartAt(LimitPeriods.unitStart(now, draft.unit(), clock.getZone()));
        }
        return state(account, limit);
    }

    @Transactional
    public void delete(String childId, String limitId) {
        Account account = accounts.managedChild(childId);
        SpendingLimit limit = limits.findByIdAndAccountId(limitId, account.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Лимит не найден"));
        limits.delete(limit);
    }

    public List<SpendingLimit> limitsOf(Account account) {
        return limits.findByAccountIdOrderByStartAtAscIdAsc(account.getId());
    }

    /** Все лимиты счёта с состоянием за текущий период. */
    public List<LimitState> states(Account account) {
        List<SpendingLimit> all = limitsOf(account);
        List<AccountOperation> spends = spendsFor(account, all);
        Instant now = Instant.now(clock);
        return all.stream().map(l -> state(l, spends, now)).toList();
    }

    /**
     * Самый жёсткий лимит для будущей операции. Вызывать после блокировки счёта —
     * тогда параллельные списания не могут изменить картину до коммита.
     */
    public Optional<LimitMath.Tight> tightest(Account account, LimitMath.Spend spend) {
        List<SpendingLimit> all = limitsOf(account);
        return LimitMath.tightest(all, spendsFor(account, all), spend, Instant.now(clock), clock.getZone());
    }

    public String name(SpendingLimit limit) {
        return LimitMath.name(limit, Instant.now(clock), clock.getZone());
    }

    private LimitState state(Account account, SpendingLimit limit) {
        return state(limit, spendsFor(account, List.of(limit)), Instant.now(clock));
    }

    private LimitState state(SpendingLimit l, List<AccountOperation> spends, Instant now) {
        return new LimitState(l, LimitMath.usage(l, spends, now, clock.getZone()), LimitMath.title(l, now, clock.getZone()),
                LimitPeriods.durationLabel(l.getCount(), l.getUnit()), LimitMath.categoriesLabel(l.getCategories()));
    }

    private List<AccountOperation> spendsFor(Account account, List<SpendingLimit> all) {
        Instant now = Instant.now(clock);
        Optional<Instant> earliest = all.stream()
                .map(l -> LimitPeriods.period(l.getCount(), l.getUnit(), l.getStartAt(), now, clock.getZone()).from())
                .min(Instant::compareTo);
        return earliest.map(from -> operations.findSpendsSince(account.getId(), from)).orElse(List.of());
    }
}
