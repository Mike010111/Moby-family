package by.moby.notification.platform.messaging;

import by.moby.common.events.IntegrationEvent;

/** Обработчик события из Kafka. Вызывается в транзакции, не больше одного раза на {@code eventId}. */
public interface EventHandler<E extends IntegrationEvent> {

    Class<E> type();

    void handle(E event);
}
