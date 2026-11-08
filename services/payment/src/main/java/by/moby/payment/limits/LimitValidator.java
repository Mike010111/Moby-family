package by.moby.payment.limits;

import by.moby.common.error.BusinessException;
import by.moby.common.error.ErrorCode;
import by.moby.common.money.Money;
import by.moby.payment.catalog.Categories;
import by.moby.payment.catalog.Cohort;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Валидация лимита при создании/изменении — порт {@code validateLimit}.
 *
 * <p>Иерархия сумм: если лимит B длиннее лимита A и покрывает все категории A, то B ≥ A.
 * Лимиты на непересекающиеся категории друг друга не ограничивают.
 */
public final class LimitValidator {

    public static final int MAX_COUNT = 50;

    private LimitValidator() {
    }

    public record Input(Integer count, String unit, String amount, List<String> categories) {
    }

    public static LimitDraft validate(Cohort cohort, List<SpendingLimit> existing, Input input, String selfId, Instant now, ZoneId zone) {
        Integer count = input.count();
        if (count == null || count < 1 || count > MAX_COUNT) {
            throw new BusinessException(ErrorCode.COUNT, "Срок — целое число от 1 до " + MAX_COUNT);
        }
        LimitUnit unit = LimitUnit.fromKey(input.unit())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNIT, "Выберите день, неделю или месяц"));
        Money amount = Money.parse(input.amount()).filter(Money::isPositive)
                .orElseThrow(() -> new BusinessException(ErrorCode.AMOUNT, "Укажите сумму — не больше двух знаков после запятой"));
        List<String> cats = List.copyOf(new LinkedHashSet<>(input.categories() == null ? List.of() : input.categories()));
        if (cats.stream().anyMatch(k -> !Categories.availableFor(cohort, k))) {
            throw new BusinessException(ErrorCode.CATEGORY, "Эта категория недоступна для счёта");
        }

        List<SpendingLimit> others = existing.stream().filter(l -> !Objects.equals(l.getId(), selfId)).toList();
        if (!cats.isEmpty()) {
            Set<String> key = new TreeSet<>(cats);
            boolean duplicate = others.stream()
                    .anyMatch(l -> !l.getCategories().isEmpty() && new TreeSet<>(l.getCategories()).equals(key));
            if (duplicate) {
                throw new BusinessException(ErrorCode.DUPLICATE, "Лимит с таким же набором категорий уже есть");
            }
        }

        int myDays = count * unit.days();
        // Новый лимит длиннее: не меньше всех более коротких лимитов, которые он покрывает
        others.stream()
                .filter(o -> o.periodDays() < myDays && covers(cats, o.getCategories()) && amount.isLessThan(o.getAmount()))
                .max(Comparator.comparing(SpendingLimit::getAmount))
                .ifPresent(shorter -> {
                    throw new BusinessException(ErrorCode.HIERARCHY, "Сумма должна быть не меньше " + shorter.getAmount().format()
                            + " — это лимит «" + LimitMath.name(shorter, now, zone) + "» на более короткий срок");
                });
        // Новый лимит короче: не больше всех более длинных лимитов, которые покрывают его категории
        others.stream()
                .filter(o -> o.periodDays() > myDays && covers(o.getCategories(), cats) && amount.isGreaterThan(o.getAmount()))
                .min(Comparator.comparing(SpendingLimit::getAmount))
                .ifPresent(longer -> {
                    throw new BusinessException(ErrorCode.HIERARCHY, "Сумма должна быть не больше " + longer.getAmount().format()
                            + " — это лимит «" + LimitMath.name(longer, now, zone) + "» на более длинный срок");
                });
        return new LimitDraft(count, unit, amount, cats);
    }

    /** Покрывает ли набор outer все траты набора inner. Пустой набор = все категории. */
    static boolean covers(List<String> outer, List<String> inner) {
        return outer.isEmpty() || (!inner.isEmpty() && outer.containsAll(inner));
    }
}
