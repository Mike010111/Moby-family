package by.moby.rewards.points;

import by.moby.common.events.IntegrationEvent;
import by.moby.common.events.LessonCompleted;
import by.moby.common.events.PaymentCompleted;
import by.moby.common.events.PiggyDeposited;
import by.moby.common.events.TaskApproved;
import by.moby.rewards.platform.messaging.EventHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Function;

/** Начисление баллов ребёнку по событиям payment и family. */
@Configuration(proxyBeanMethods = false)
class PointHandlers {

    @Bean
    EventHandler<PaymentCompleted> pointsForPayment(PointsService points) {
        return handler(PaymentCompleted.class, points,
                e -> PointsService.forPurchase(e.amount().amount()), e -> "Покупка: " + e.merchantName());
    }

    @Bean
    EventHandler<TaskApproved> pointsForTask(PointsService points) {
        return handler(TaskApproved.class, points, TaskApproved::points, e -> "Задание: " + e.title());
    }

    @Bean
    EventHandler<LessonCompleted> pointsForLesson(PointsService points) {
        return handler(LessonCompleted.class, points, LessonCompleted::points, e -> "Урок " + e.lessonsDone() + "/" + e.lessonsTotal());
    }

    @Bean
    EventHandler<PiggyDeposited> pointsForPiggy(PointsService points) {
        return handler(PiggyDeposited.class, points, PiggyDeposited::points, e -> "Пополнение копилки");
    }

    private static <E extends IntegrationEvent> EventHandler<E> handler(Class<E> type, PointsService points,
                                                                         Function<E, Integer> amount, Function<E, String> reason) {
        return new EventHandler<>() {
            @Override
            public Class<E> type() {
                return type;
            }

            @Override
            public void handle(E event) {
                points.credit(event.childId(), amount.apply(event), reason.apply(event));
            }
        };
    }
}
