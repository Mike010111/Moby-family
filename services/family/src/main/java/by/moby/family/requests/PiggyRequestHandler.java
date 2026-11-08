package by.moby.family.requests;

import by.moby.common.money.Money;

/**
 * Исполнение одобренных запросов по копилкам. Реализует модуль piggies — так requests
 * не зависит от piggies (зависимость направлена piggies → requests).
 */
public interface PiggyRequestHandler {

    /** Итог для уведомления: цель копилки и сколько вернулось на счёт (для удаления). */
    record Outcome(String goal, Money returned) {
    }

    /** Пусто — копилки уже нет; запрос будет отменён. */
    java.util.Optional<Outcome> approveUpdate(ChildRequest request);

    java.util.Optional<Outcome> approveDelete(ChildRequest request);

    java.util.Optional<String> goalOf(ChildRequest request);
}
