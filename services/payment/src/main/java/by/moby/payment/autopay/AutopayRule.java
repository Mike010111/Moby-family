package by.moby.payment.autopay;

import by.moby.common.money.Money;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * Автоперевод главный родственник → ребёнок. {@code weekday}: 0 — понедельник … 6 — воскресенье
 * (как DAYS_ACC в rules.js); {@code monthDay}: 1…28.
 */
@Entity
@Table(name = "autopay_rules")
public class AutopayRule {

    @Id
    private String accountId;
    private boolean enabled;
    private Money amount;
    @Convert(converter = AutopayEveryConverter.class)
    private AutopayEvery every;
    private int weekday;
    private int monthDay;
    private LocalDate lastRunOn;

    protected AutopayRule() {
    }

    public AutopayRule(String accountId, boolean enabled, Money amount, AutopayEvery every, int weekday, int monthDay, LocalDate lastRunOn) {
        this.accountId = accountId;
        this.enabled = enabled;
        this.amount = amount;
        this.every = every;
        this.weekday = weekday;
        this.monthDay = monthDay;
        this.lastRunOn = lastRunOn;
    }

    /** Как {@code autopay/update}: день недели / число месяца меняются только для своей периодичности. */
    void update(boolean enabled, Money amount, AutopayEvery every, Integer weekday, Integer monthDay) {
        this.enabled = enabled;
        this.amount = amount;
        this.every = every;
        if (every == AutopayEvery.WEEK) {
            this.weekday = weekday;
        }
        if (every == AutopayEvery.MONTH) {
            this.monthDay = monthDay;
        }
    }

    /** Пора ли переводить сегодня: расписание совпало и сегодня ещё не переводили. */
    public boolean isDue(LocalDate today) {
        if (!enabled || today.equals(lastRunOn)) {
            return false;
        }
        return switch (every) {
            case DAY -> true;
            case WEEK -> today.getDayOfWeek().getValue() - 1 == weekday;
            case MONTH -> today.getDayOfMonth() == monthDay;
        };
    }

    void markRun(LocalDate today) {
        this.lastRunOn = today;
    }

    public String getAccountId() {
        return accountId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Money getAmount() {
        return amount;
    }

    public AutopayEvery getEvery() {
        return every;
    }

    public int getWeekday() {
        return weekday;
    }

    public int getMonthDay() {
        return monthDay;
    }

    public LocalDate getLastRunOn() {
        return lastRunOn;
    }
}
