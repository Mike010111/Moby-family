package by.moby.payment.payments;

/**
 * Что даёт оплата по правилам rewards: баллы и кэшбэк действующего артефакта маскота.
 * {@code cashback} — null, если подходящего артефакта нет.
 */
public record Perks(Integer points, Cashback cashback) {

    public record Cashback(int percent, String artifactId, String artifactName) {
    }

    public static Perks unknown() {
        return new Perks(null, null);
    }
}
