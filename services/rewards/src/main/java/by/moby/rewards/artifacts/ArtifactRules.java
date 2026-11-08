package by.moby.rewards.artifacts;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/** Чистые правила магазина — порт {@code offerState}, {@code renewOffer}, {@code cashbackFor}. */
public final class ArtifactRules {

    public enum OfferStatus { AVAILABLE, EQUIPPED, SOLDOUT, NOPOINTS }

    public record Offer(OfferStatus status, int price, int lack, OwnedArtifact replaces) {
    }

    public record RenewOffer(OwnedArtifact artifact, int cashback, Rarity rarity, int price) {
    }

    private ArtifactRules() {
    }

    public static Offer offer(ArtifactState state, Optional<OwnedArtifact> current, int points, ArtifactCatalog.Artifact artifact, Instant now) {
        int price = artifact.rarity().price();
        boolean active = current.filter(c -> c.isActive(now)).isPresent();
        if (active && current.get().getArtifactId().equals(artifact.id())) {
            return new Offer(OfferStatus.EQUIPPED, price, 0, null);
        }
        if (state.isSoldOut(artifact.id())) {
            return new Offer(OfferStatus.SOLDOUT, price, 0, null);
        }
        if (points < price) {
            return new Offer(OfferStatus.NOPOINTS, price, price - points, null);
        }
        return new Offer(OfferStatus.AVAILABLE, price, 0, active ? current.get() : null);
    }

    /**
     * Продлить свой артефакт можно только в неделю его слота и только если цикл истёк.
     * Кэшбэк −1 % за цикл, но не ниже 3 %; цена — по редкости нового кэшбэка.
     */
    public static Optional<RenewOffer> renewOffer(ArtifactState state, Optional<OwnedArtifact> own, Instant now) {
        if (own.isEmpty() || own.get().isActive(now) || !now.isBefore(state.weekEnd())) {
            return Optional.empty();
        }
        int cashback = Math.max(Rarity.MIN_CASHBACK, own.get().getCashback() - 1);
        Rarity rarity = Rarity.byCashback(cashback);
        return Optional.of(new RenewOffer(own.get(), cashback, rarity, rarity.price()));
    }

    /** Лучший действующий кэшбэк на категорию покупки. */
    public static Optional<OwnedArtifact> cashbackFor(Collection<OwnedArtifact> owned, String category, Instant now) {
        return owned.stream()
                .filter(o -> o.isActive(now) && o.getCategory().equals(category))
                .max(Comparator.comparingInt(OwnedArtifact::getCashback));
    }
}
