package by.moby.payment.limits;

import by.moby.common.money.Money;

/**
 * Состояние лимита за текущий период. Корректно, даже если лимит меньше уже потраченного:
 * remaining = 0, exhausted = true, overBy > 0.
 */
public record LimitUsage(Money spent, Money remaining, boolean exhausted, Money overBy, int percent) {
}
