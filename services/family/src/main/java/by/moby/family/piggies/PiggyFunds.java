package by.moby.family.piggies;

import by.moby.common.events.PiggyCompleted;
import by.moby.common.money.Money;
import by.moby.family.members.Member;
import by.moby.family.payment.PaymentClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Закрытие копилки: деньги с SAVINGS-счёта — на карту ребёнка. Ключи идемпотентности — по id копилки. */
@Component
class PiggyFunds {

    record Completed(String goal, Money amount) {
    }

    private final PaymentClient payment;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    PiggyFunds(PaymentClient payment, ApplicationEventPublisher events, Clock clock) {
        this.payment = payment;
        this.events = events;
        this.clock = clock;
    }

    /** Цель достигнута: деньги на счёт, событие для уведомлений ребёнку и главному родственнику. */
    Completed complete(Member child, Piggy piggy) {
        PaymentClient.SavingsResult result = payment.withdrawSavings(piggy.getId(), "Копилка «" + piggy.getGoal() + "» собрана",
                "piggy-complete:" + piggy.getId());
        piggy.complete();
        events.publishEvent(new PiggyCompleted(UUID.randomUUID(), Instant.now(clock), child.getFamilyId(), child.getId(), child.getName(),
                piggy.getId(), piggy.getGoal(), result.amount()));
        return new Completed(piggy.getGoal(), result.amount());
    }

    /** Удаление: всё накопленное возвращается на счёт. */
    Money closeAndReturn(Piggy piggy) {
        PaymentClient.SavingsResult result = payment.withdrawSavings(piggy.getId(), "Возврат из копилки «" + piggy.getGoal() + "»",
                "piggy-close:" + piggy.getId());
        piggy.closeAndReturn();
        return result.amount();
    }
}
