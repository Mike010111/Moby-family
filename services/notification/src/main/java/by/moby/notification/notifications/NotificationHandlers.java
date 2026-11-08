package by.moby.notification.notifications;

import by.moby.common.events.AccountFrozen;
import by.moby.common.events.AccountUnfrozen;
import by.moby.common.events.ArtifactPurchased;
import by.moby.common.events.CashbackGranted;
import by.moby.common.events.IntegrationEvent;
import by.moby.common.events.LessonCompleted;
import by.moby.common.events.PaymentCompleted;
import by.moby.common.events.PaymentDeclined;
import by.moby.common.events.PiggyCompleted;
import by.moby.common.events.PiggyDeposited;
import by.moby.common.events.RequestCreated;
import by.moby.common.events.RequestResolved;
import by.moby.common.events.TaskApproved;
import by.moby.common.events.TaskSubmitted;
import by.moby.common.events.TransferCompleted;
import by.moby.notification.platform.messaging.EventHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.BiConsumer;

/** Какие события превращаются в ленту родителя и уведомления ребёнку (порядок и тексты — как в actions.js). */
@Configuration(proxyBeanMethods = false)
class NotificationHandlers {

    @Bean
    EventHandler<RequestCreated> onRequestCreated(NotificationService n) {
        return on(RequestCreated.class, n, (e, s) -> {
            if (e.toMain()) { // окно в демо есть только у главного родственника
                s.feed(e.familyId(), e.childId(), NotificationTexts.requestCreated(e), e.occurredAt(), e.requestId(), null);
            }
        });
    }

    @Bean
    EventHandler<RequestResolved> onRequestResolved(NotificationService n) {
        return on(RequestResolved.class, n, (e, s) -> s.inbox(e.familyId(), e.childId(), NotificationTexts.requestResolved(e), e.occurredAt()));
    }

    @Bean
    EventHandler<TaskSubmitted> onTaskSubmitted(NotificationService n) {
        return on(TaskSubmitted.class, n, (e, s) -> s.feed(e.familyId(), e.childId(), NotificationTexts.taskSubmitted(e), e.occurredAt(), null, e.taskId()));
    }

    @Bean
    EventHandler<TaskApproved> onTaskApproved(NotificationService n) {
        return on(TaskApproved.class, n, (e, s) -> s.inbox(e.familyId(), e.childId(), NotificationTexts.taskApproved(e), e.occurredAt()));
    }

    @Bean
    EventHandler<PiggyCompleted> onPiggyCompleted(NotificationService n) {
        return on(PiggyCompleted.class, n, (e, s) -> {
            s.inbox(e.familyId(), e.childId(), NotificationTexts.piggyCompletedInbox(e), e.occurredAt());
            s.feed(e.familyId(), e.childId(), NotificationTexts.piggyCompletedFeed(e), e.occurredAt(), null, null);
        });
    }

    @Bean
    EventHandler<AccountFrozen> onAccountFrozen(NotificationService n) {
        return on(AccountFrozen.class, n, (e, s) -> {
            s.feed(e.familyId(), e.childId(), NotificationTexts.frozen(e), e.occurredAt(), null, null);
            s.inbox(e.familyId(), e.childId(), NotificationTexts.frozenInbox(e), e.occurredAt());
        });
    }

    @Bean
    EventHandler<AccountUnfrozen> onAccountUnfrozen(NotificationService n) {
        return on(AccountUnfrozen.class, n, (e, s) -> {
            s.feed(e.familyId(), e.childId(), NotificationTexts.unfrozen(e), e.occurredAt(), null, null);
            s.inbox(e.familyId(), e.childId(), NotificationTexts.unfrozenInbox(e), e.occurredAt());
        });
    }

    @Bean
    EventHandler<PaymentDeclined> onPaymentDeclined(NotificationService n) {
        return on(PaymentDeclined.class, n, (e, s) -> s.feed(e.familyId(), e.childId(), NotificationTexts.paymentDeclined(e), e.occurredAt(), null, null));
    }

    @Bean
    EventHandler<PaymentCompleted> onPaymentCompleted(NotificationService n) {
        return on(PaymentCompleted.class, n, (e, s) -> s.feed(e.familyId(), e.childId(), NotificationTexts.paymentCompleted(e), e.occurredAt(), null, null));
    }

    // События без записи в ленту — только сигнал экрану обновить данные
    @Bean
    EventHandler<TransferCompleted> onTransfer(NotificationService n) {
        return ping(TransferCompleted.class, n);
    }

    @Bean
    EventHandler<CashbackGranted> onCashback(NotificationService n) {
        return ping(CashbackGranted.class, n);
    }

    @Bean
    EventHandler<ArtifactPurchased> onArtifact(NotificationService n) {
        return ping(ArtifactPurchased.class, n);
    }

    @Bean
    EventHandler<LessonCompleted> onLesson(NotificationService n) {
        return ping(LessonCompleted.class, n);
    }

    @Bean
    EventHandler<PiggyDeposited> onPiggyDeposit(NotificationService n) {
        return ping(PiggyDeposited.class, n);
    }

    private static <E extends IntegrationEvent> EventHandler<E> ping(Class<E> type, NotificationService n) {
        return on(type, n, (e, s) -> s.pingAfterCommit(e.familyId(), e.childId(), type.getSimpleName()));
    }

    private static <E extends IntegrationEvent> EventHandler<E> on(Class<E> type, NotificationService n, BiConsumer<E, NotificationService> action) {
        return new EventHandler<>() {
            @Override
            public Class<E> type() {
                return type;
            }

            @Override
            public void handle(E event) {
                action.accept(event, n);
            }
        };
    }
}
