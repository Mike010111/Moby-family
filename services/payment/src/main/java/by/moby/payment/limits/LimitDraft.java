package by.moby.payment.limits;

import by.moby.common.money.Money;

import java.util.List;

/** Проверенные параметры лимита. */
public record LimitDraft(int count, LimitUnit unit, Money amount, List<String> categories) {

    public LimitDraft {
        categories = List.copyOf(categories);
    }
}
