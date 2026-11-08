package by.moby.rewards.platform.messaging;

/** Заголовки Kafka-сообщений с событиями: по ним консьюмер выбирает тип и проверяет повтор. */
public final class EventHeaders {

    /** Простое имя класса события из {@code by.moby.common.events}. */
    public static final String TYPE = "eventType";
    public static final String ID = "eventId";

    private EventHeaders() {
    }
}
