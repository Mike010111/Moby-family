package by.moby.payment.payments;

import by.moby.common.money.Money;

/** Синхронный запрос к rewards: процент кэшбэка и баллы за покупку. */
public interface RewardsClient {

    /** Никогда не бросает: при недоступности rewards оплата проходит без кэшбэка. */
    Perks perks(String childId, String category, Money amount);
}
