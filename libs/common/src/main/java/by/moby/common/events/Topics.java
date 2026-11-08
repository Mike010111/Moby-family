package by.moby.common.events;

/** Kafka-топики: по одному на сервис-источник. */
public final class Topics {

    public static final String PAYMENT = "payment.events";
    public static final String FAMILY = "family.events";
    public static final String REWARDS = "rewards.events";

    private Topics() {
    }
}
