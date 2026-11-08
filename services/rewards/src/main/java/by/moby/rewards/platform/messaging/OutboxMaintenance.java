package by.moby.rewards.platform.messaging;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Обслуживание outbox: повторная отправка событий, не дошедших до Kafka (брокер был недоступен),
 * и чистка отправленных. ShedLock — чтобы при нескольких репликах это делала одна.
 */
@Component
@ConditionalOnProperty(name = "spring.modulith.events.externalization.enabled", havingValue = "true", matchIfMissing = true)
class OutboxMaintenance {

    private final IncompleteEventPublications incomplete;
    private final CompletedEventPublications completed;

    OutboxMaintenance(IncompleteEventPublications incomplete, CompletedEventPublications completed) {
        this.incomplete = incomplete;
        this.completed = completed;
    }

    @Scheduled(fixedDelayString = "${moby.outbox.resubmit-every:PT30S}")
    @SchedulerLock(name = "outbox-resubmit", lockAtMostFor = "PT5M")
    void resubmit() {
        incomplete.resubmitIncompletePublicationsOlderThan(Duration.ofSeconds(30));
    }

    @Scheduled(cron = "${moby.outbox.cleanup-cron:0 0 3 * * *}", zone = "Europe/Minsk")
    @SchedulerLock(name = "outbox-cleanup", lockAtMostFor = "PT10M")
    void cleanup() {
        completed.deletePublicationsOlderThan(Duration.ofDays(7));
    }
}
