package by.moby.payment.limits;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

/**
 * Календарные периоды лимитов — порт {@code unitStart}, {@code limitPeriod}, {@code periodTitle},
 * {@code periodLabel} из rules.js. Все вычисления в календаре заданной зоны (Europe/Minsk).
 */
public final class LimitPeriods {

    private static final String[] MONTHS = {"Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"};
    private static final String[] MONTHS_GEN = {"января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря"};
    private static final String[] MONTHS_SHORT = {"янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек"};

    /** Период [from, to). */
    public record Period(Instant from, Instant to) {

        public boolean contains(Instant ts) {
            return !ts.isBefore(from) && ts.isBefore(to);
        }
    }

    private LimitPeriods() {
    }

    /** Начало календарной единицы: полночь, понедельник этой недели, 1-е число месяца. */
    public static Instant unitStart(Instant ts, LimitUnit unit, ZoneId zone) {
        return startDate(ts.atZone(zone).toLocalDate(), unit).atStartOfDay(zone).toInstant();
    }

    private static LocalDate startDate(LocalDate date, LimitUnit unit) {
        return switch (unit) {
            case DAY -> date;
            case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> date.withDayOfMonth(1);
        };
    }

    private static LocalDate add(LocalDate date, LimitUnit unit, long n) {
        return switch (unit) {
            case DAY -> date.plusDays(n);
            case WEEK -> date.plusWeeks(n);
            case MONTH -> date.plusMonths(n);
        };
    }

    private static long unitsBetween(LocalDate from, LocalDate to, LimitUnit unit) {
        return switch (unit) {
            case MONTH -> (to.getYear() - from.getYear()) * 12L + to.getMonthValue() - from.getMonthValue();
            case DAY -> ChronoUnit.DAYS.between(from, to);
            case WEEK -> Math.floorDiv(ChronoUnit.DAYS.between(from, to), 7);
        };
    }

    /**
     * Текущий период лимита: периоды по {@code count} единиц идут подряд от {@code start}
     * (начало дня / недели / месяца при создании лимита).
     */
    public static Period period(int count, LimitUnit unit, Instant start, Instant now, ZoneId zone) {
        LocalDate today = startDate(now.atZone(zone).toLocalDate(), unit);
        LocalDate anchor = start == null ? today : start.atZone(zone).toLocalDate();
        long elapsed = Math.max(0, unitsBetween(anchor, today, unit));
        LocalDate from = add(anchor, unit, elapsed - (elapsed % count));
        LocalDate to = add(from, unit, count);
        return new Period(from.atStartOfDay(zone).toInstant(), to.atStartOfDay(zone).toInstant());
    }

    /** «Сегодня», «Эта неделя», «Октябрь»; для сроков больше 1 — даты: «21–23 октября». */
    public static String title(int count, LimitUnit unit, Instant start, Instant now, ZoneId zone) {
        Period p = period(count, unit, start, now, zone);
        LocalDate a = p.from().atZone(zone).toLocalDate();
        if (count == 1) {
            return switch (unit) {
                case DAY -> "Сегодня";
                case WEEK -> "Эта неделя";
                case MONTH -> MONTHS[a.getMonthValue() - 1];
            };
        }
        LocalDate b = p.to().atZone(zone).toLocalDate().minusDays(1);
        if (unit == LimitUnit.MONTH) {
            return MONTHS[a.getMonthValue() - 1] + " – " + MONTHS[b.getMonthValue() - 1];
        }
        if (a.getMonth() == b.getMonth()) {
            return a.getDayOfMonth() + "–" + b.getDayOfMonth() + " " + MONTHS_GEN[a.getMonthValue() - 1];
        }
        return a.getDayOfMonth() + " " + MONTHS_SHORT[a.getMonthValue() - 1] + " – " + b.getDayOfMonth() + " " + MONTHS_SHORT[b.getMonthValue() - 1];
    }

    /** Длительность срока — «1 неделя», «3 дня». */
    public static String durationLabel(int count, LimitUnit unit) {
        return count + " " + unit.form(count);
    }
}
