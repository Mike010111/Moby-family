package by.moby.payment.limits;

import by.moby.common.money.Money;
import by.moby.payment.account.AccountOperation;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LimitMathTest {

    static final ZoneId MINSK = ZoneId.of("Europe/Minsk");
    static final Instant NOW = ZonedDateTime.of(2026, 10, 21, 9, 0, 0, 0, MINSK).toInstant();

    static SpendingLimit limit(String amount, List<String> categories) {
        return new SpendingLimit("l", "acc", new LimitDraft(1, LimitUnit.DAY, Money.of(amount), categories),
                LimitPeriods.unitStart(NOW, LimitUnit.DAY, MINSK));
    }

    static AccountOperation spend(String amount, String mcc, Instant at, boolean online) {
        return AccountOperation.history("op-" + at, "acc", "t", Money.of(amount), mcc, online, at);
    }

    /** «Траты считаются за календарный период: вчерашняя покупка не входит в „Сегодня“». */
    @Test
    void yesterdaySpendIsNotCountedToday() {
        var ops = List.of(spend("-5", "5462", NOW.minus(Duration.ofHours(10)), false));
        assertThat(LimitMath.usage(limit("10", List.of()), ops, NOW, MINSK).spent()).isEqualTo(Money.ZERO);
    }

    @Test
    void usageWhenLimitIsBelowSpent() {
        var ops = List.of(spend("-3.60", "5462", NOW.minus(Duration.ofHours(1)), false));
        LimitUsage u = LimitMath.usage(limit("1", List.of()), ops, NOW, MINSK);
        assertThat(u.remaining()).isEqualTo(Money.ZERO);
        assertThat(u.exhausted()).isTrue();
        assertThat(u.percent()).isEqualTo(100);
        assertThat(u.overBy()).isEqualTo(Money.of("2.60"));
    }

    @Test
    void serviceOperationsAndOtherCategoriesDoNotCount() {
        Instant t = NOW.minus(Duration.ofHours(1));
        var ops = List.of(spend("-2", "piggy", t, false), spend("-4", "5942", t, false), spend("-1", "5812", t, false),
                spend("-6.99", "5735", t, true));
        assertThat(LimitMath.usage(limit("100", List.of("cafe")), ops, NOW, MINSK).spent()).isEqualTo(Money.of("1.00"));
        assertThat(LimitMath.usage(limit("100", List.of("online")), ops, NOW, MINSK).spent()).isEqualTo(Money.of("6.99"));
        assertThat(LimitMath.usage(limit("100", List.of()), ops, NOW, MINSK).spent()).isEqualTo(Money.of("11.99"));
    }

    @Test
    void tightestIsTheLimitWithLeastRemaining() {
        SpendingLimit all = limit("15", List.of());
        SpendingLimit cafe = new SpendingLimit("c", "acc", new LimitDraft(1, LimitUnit.WEEK, Money.of("5"), List.of("cafe")),
                LimitPeriods.unitStart(NOW, LimitUnit.WEEK, MINSK));
        var tight = LimitMath.tightest(List.of(all, cafe), List.of(), new LimitMath.Spend("5462", false, Money.of("1")), NOW, MINSK);
        assertThat(tight).get().extracting(t -> t.limit().getId()).isEqualTo("c");
        assertThat(LimitMath.tightest(List.of(cafe), List.of(), new LimitMath.Spend("5942", false, Money.of("1")), NOW, MINSK)).isEmpty();
    }
}
