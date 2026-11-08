package by.moby.notification.demo;

import by.moby.notification.notifications.Notification;
import by.moby.notification.notifications.NotificationRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Лента из initial-state.js: одно событие «Иван оплатил «Кофейня «Зерно»»». */
@Component
@Profile("demo")
public class NotificationDemoData {

    public static final String FAMILY = "fam-demo";

    private final NotificationRepository notifications;
    private final JdbcClient jdbc;
    private final Clock clock;

    public NotificationDemoData(NotificationRepository notifications, JdbcClient jdbc, Clock clock) {
        this.notifications = notifications;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public void seedIfEmpty() {
        if (notifications.count() == 0) {
            seed();
        }
    }

    @Transactional
    public void reset() {
        jdbc.sql("truncate notifications, processed_events").update();
        seed();
    }

    private void seed() {
        Instant now = Instant.now(clock);
        notifications.save(Notification.feed(FAMILY, "ivan", "info", "Иван оплатил «Кофейня «Зерно»»", "6,50 BYN · Кафе и столовые",
                now.minus(Duration.ofHours(2))));
    }
}
