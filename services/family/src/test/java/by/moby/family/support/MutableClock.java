package by.moby.family.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Управляемые часы для тестов: по умолчанию — среда, 21 октября 2026, 12:00 по Минску. */
public class MutableClock extends Clock {

    public static final ZoneId MINSK = ZoneId.of("Europe/Minsk");
    public static final Instant DEFAULT = ZonedDateTime.of(2026, 10, 21, 12, 0, 0, 0, MINSK).toInstant();

    private volatile Instant now = DEFAULT;

    public void reset() {
        now = DEFAULT;
    }

    public void set(Instant instant) {
        now = instant;
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return MINSK;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(now, zone);
    }

    @Override
    public Instant instant() {
        return now;
    }
}
