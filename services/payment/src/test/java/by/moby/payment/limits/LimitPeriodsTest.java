package by.moby.payment.limits;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Аналог теста «Названия периодов…» из store.test.js. */
class LimitPeriodsTest {

    static final ZoneId MINSK = ZoneId.of("Europe/Minsk");
    static final Instant NOW = ZonedDateTime.of(2026, 10, 21, 12, 0, 0, 0, MINSK).toInstant(); // среда

    static String title(int count, LimitUnit unit) {
        return LimitPeriods.title(count, unit, LimitPeriods.unitStart(NOW, unit, MINSK), NOW, MINSK);
    }

    @Test
    void periodTitlesMatchThePrototype() {
        assertThat(title(1, LimitUnit.DAY)).isEqualTo("Сегодня");
        assertThat(title(1, LimitUnit.WEEK)).isEqualTo("Эта неделя");
        assertThat(title(1, LimitUnit.MONTH)).isEqualTo("Октябрь");
        assertThat(title(3, LimitUnit.DAY)).isEqualTo("21–23 октября");
        assertThat(title(2, LimitUnit.WEEK)).isEqualTo("19 окт – 1 ноя");
        assertThat(title(3, LimitUnit.MONTH)).isEqualTo("Октябрь – Декабрь");
    }

    @Test
    void periodStartedTwoDaysAgoIsStillRunning() {
        Instant start = LimitPeriods.unitStart(NOW.minus(Duration.ofDays(2)), LimitUnit.DAY, MINSK);
        assertThat(LimitPeriods.title(3, LimitUnit.DAY, start, NOW, MINSK)).isEqualTo("19–21 октября");
    }

    @Test
    void periodsRollOverCalendarUnits() {
        Instant start = LimitPeriods.unitStart(NOW.minus(Duration.ofDays(3)), LimitUnit.DAY, MINSK); // 18 октября
        LimitPeriods.Period p = LimitPeriods.period(3, LimitUnit.DAY, start, NOW, MINSK);
        assertThat(p.from()).isEqualTo(ZonedDateTime.of(2026, 10, 21, 0, 0, 0, 0, MINSK).toInstant());
        assertThat(p.to()).isEqualTo(ZonedDateTime.of(2026, 10, 24, 0, 0, 0, 0, MINSK).toInstant());
    }

    @Test
    void unitStartIsMidnightMondayAndFirstOfMonthInMinsk() {
        assertThat(LimitPeriods.unitStart(NOW, LimitUnit.WEEK, MINSK)).isEqualTo(ZonedDateTime.of(2026, 10, 19, 0, 0, 0, 0, MINSK).toInstant());
        assertThat(LimitPeriods.unitStart(NOW, LimitUnit.MONTH, MINSK)).isEqualTo(ZonedDateTime.of(2026, 10, 1, 0, 0, 0, 0, MINSK).toInstant());
    }

    @Test
    void durationLabelsArePluralised() {
        assertThat(LimitPeriods.durationLabel(1, LimitUnit.WEEK)).isEqualTo("1 неделя");
        assertThat(LimitPeriods.durationLabel(3, LimitUnit.DAY)).isEqualTo("3 дня");
        assertThat(LimitPeriods.durationLabel(11, LimitUnit.MONTH)).isEqualTo("11 месяцев");
        assertThat(LimitPeriods.durationLabel(21, LimitUnit.DAY)).isEqualTo("21 день");
    }
}
