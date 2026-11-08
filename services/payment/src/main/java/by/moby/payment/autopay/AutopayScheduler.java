package by.moby.payment.autopay;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Запуск автопереводов. ShedLock: при нескольких репликах payment задача идёт на одной. */
@Component
@ConditionalOnProperty(name = "moby.autopay.enabled", havingValue = "true", matchIfMissing = true)
class AutopayScheduler {

    private static final Logger log = LoggerFactory.getLogger(AutopayScheduler.class);

    private final AutopayService autopay;

    AutopayScheduler(AutopayService autopay) {
        this.autopay = autopay;
    }

    @Scheduled(cron = "${moby.autopay.cron:0 */15 * * * *}", zone = "Europe/Minsk")
    @SchedulerLock(name = "autopay", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    void run() {
        int done = autopay.runDue();
        if (done > 0) {
            log.info("autopay: {} transfers", done);
        }
    }
}
