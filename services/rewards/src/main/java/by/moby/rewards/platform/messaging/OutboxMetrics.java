package by.moby.rewards.platform.messaging;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** {@code moby_outbox_pending}: сколько событий ещё не доставлено в Kafka (растёт, если брокер недоступен). */
@Component
class OutboxMetrics implements MeterBinder {

    private final JdbcClient jdbc;

    OutboxMetrics(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("moby.outbox.pending", this::pending)
                .description("Incomplete event publications in the transactional outbox")
                .register(registry);
    }

    private double pending() {
        return jdbc.sql("select count(*) from event_publication where completion_date is null").query(Long.class).single();
    }
}
