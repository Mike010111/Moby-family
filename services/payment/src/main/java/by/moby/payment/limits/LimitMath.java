package by.moby.payment.limits;

import by.moby.common.money.Money;
import by.moby.payment.account.AccountOperation;
import by.moby.payment.catalog.Categories;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** Чистые функции лимитов: {@code txMatchesLimit}, {@code limitUsage}, {@code tightestLimit}, подписи. */
public final class LimitMath {

    private LimitMath() {
    }

    /** Будущая или совершённая операция с точки зрения лимитов. */
    public record Spend(String mcc, boolean online, Money amount) {
    }

    /** Самый жёсткий лимит для операции и его состояние. */
    public record Tight(SpendingLimit limit, LimitUsage usage) {
    }

    /** Попадает ли операция под лимит. Пустой список категорий = все траты. */
    public static boolean matches(String mcc, boolean online, SpendingLimit limit) {
        String group = Categories.groupOf(mcc);
        if (Categories.isService(group)) {
            return false;
        }
        List<String> cats = limit.getCategories();
        if (cats.isEmpty()) {
            return true;
        }
        return cats.contains(group) || (online && cats.contains(Categories.ONLINE));
    }

    public static LimitUsage usage(SpendingLimit limit, Collection<AccountOperation> operations, Instant now, ZoneId zone) {
        LimitPeriods.Period period = LimitPeriods.period(limit.getCount(), limit.getUnit(), limit.getStartAt(), now, zone);
        Money spent = operations.stream()
                .filter(AccountOperation::isSpend)
                .filter(o -> period.contains(o.getCreatedAt()))
                .filter(o -> matches(o.getMcc(), o.isOnline(), limit))
                .map(o -> o.getAmount().negate())
                .reduce(Money.ZERO, Money::plus);
        Money amount = limit.getAmount();
        Money remaining = Money.max(Money.ZERO, amount.minus(spent));
        boolean exhausted = !spent.isLessThan(amount);
        Money overBy = Money.max(Money.ZERO, spent.minus(amount));
        int percent = amount.isPositive()
                ? Math.min(100, spent.amount().multiply(BigDecimal.valueOf(100)).divide(amount.amount(), 0, RoundingMode.HALF_UP).intValue())
                : 100;
        return new LimitUsage(spent, remaining, exhausted, overBy, percent);
    }

    public static Optional<Tight> tightest(List<SpendingLimit> limits, Collection<AccountOperation> operations, Spend op, Instant now, ZoneId zone) {
        Tight best = null;
        for (SpendingLimit limit : limits) {
            if (!matches(op.mcc(), op.online(), limit)) {
                continue;
            }
            LimitUsage usage = usage(limit, operations, now, zone);
            if (best == null || usage.remaining().isLessThan(best.usage().remaining())) {
                best = new Tight(limit, usage);
            }
        }
        return Optional.ofNullable(best);
    }

    public static String categoriesLabel(List<String> categories) {
        if (categories.isEmpty()) {
            return "Все категории";
        }
        return categories.stream().map(Categories::label).collect(Collectors.joining(", "));
    }

    public static String title(SpendingLimit limit, Instant now, ZoneId zone) {
        return LimitPeriods.title(limit.getCount(), limit.getUnit(), limit.getStartAt(), now, zone);
    }

    /** «Сегодня · Все категории» — как лимит называется в причинах отказа. */
    public static String name(SpendingLimit limit, Instant now, ZoneId zone) {
        return title(limit, now, zone) + " · " + categoriesLabel(limit.getCategories());
    }
}
