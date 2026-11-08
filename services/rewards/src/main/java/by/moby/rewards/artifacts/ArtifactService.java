package by.moby.rewards.artifacts;

import by.moby.common.error.BusinessException;
import by.moby.common.error.ErrorCode;
import by.moby.common.events.ArtifactPurchased;
import by.moby.common.money.Money;
import by.moby.rewards.points.PointAccount;
import by.moby.rewards.points.PointsService;
import by.moby.rewards.platform.security.AccessPolicy;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Магазин артефактов маскота ({@code artifact/buy}, {@code artifact/renew}). Платят баллами ребёнка;
 * баллы не блокируются вместе со счётом, поэтому магазин работает и при заблокированном счёте.
 */
@Service
public class ArtifactService {

    private final ArtifactStateRepository states;
    private final OwnedArtifactRepository owned;
    private final PointsService points;
    private final AccessPolicy access;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ArtifactService(ArtifactStateRepository states, OwnedArtifactRepository owned, PointsService points, AccessPolicy access,
                           ApplicationEventPublisher events, Clock clock) {
        this.states = states;
        this.owned = owned;
        this.points = points;
        this.access = access;
        this.events = events;
        this.clock = clock;
    }

    public record Purchase(OwnedArtifact artifact, String replaced, int price, int points) {
    }

    public record Snapshot(ArtifactState state, List<OwnedArtifact> owned, int points, Instant now) {
    }

    public record Perks(int points, Cashback cashback) {

        public record Cashback(int percent, String artifactId, String artifactName) {
        }
    }

    @Transactional(readOnly = true)
    public Snapshot snapshot(String childId) {
        ArtifactState state = state(childId);
        access.requireChildOrParent(state.getFamilyId(), childId);
        return new Snapshot(state, owned.findByChildId(childId), points.account(childId).getPoints(), Instant.now(clock));
    }

    /** Покупка артефакта недели. Новый артефакт заменяет прежний того же слота. */
    @Transactional
    public Purchase buy(String childId, String artifactId) {
        access.requireSelf(childId);
        PointAccount balance = points.lock(childId);
        ArtifactState state = state(childId);
        ArtifactCatalog.Artifact artifact = ArtifactCatalog.byId(artifactId)
                .filter(a -> a.slot() == state.getWeekSlot() && ArtifactCatalog.shopFor(state.getWeekSlot()).contains(a.id()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOR_SALE, "Этот артефакт сейчас не продаётся"));
        Instant now = Instant.now(clock);
        Optional<OwnedArtifact> current = owned.findByChildIdAndSlot(childId, artifact.slot());
        ArtifactRules.Offer offer = ArtifactRules.offer(state, current, balance.getPoints(), artifact, now);
        switch (offer.status()) {
            case EQUIPPED -> throw new BusinessException(ErrorCode.EQUIPPED, "Этот артефакт уже у маскота");
            case SOLDOUT -> throw new BusinessException(ErrorCode.SOLDOUT, "Уже покупали на этой неделе — снова будет в следующем цикле");
            case NOPOINTS -> throw new BusinessException(ErrorCode.POINTS, "Не хватает " + offer.lack() + " баллов");
            case AVAILABLE -> { }
        }
        String replaced = offer.replaces() == null ? null : offer.replaces().getName();
        PointAccount after = points.debit(childId, offer.price(), "Артефакт: " + artifact.name());
        OwnedArtifact own = current.map(c -> {
            c.replaceWith(artifact, state.getCohort(), now, state.purchaseActiveUntil());
            return c;
        }).orElseGet(() -> owned.save(new OwnedArtifact(childId, artifact, state.getCohort(), now, state.purchaseActiveUntil())));
        state.markSoldOut(artifact.id());
        events.publishEvent(new ArtifactPurchased(UUID.randomUUID(), now, state.getFamilyId(), childId, artifact.id(), artifact.name(),
                offer.price(), own.getCashback(), own.getCategory(), false));
        return new Purchase(own, replaced, offer.price(), after.getPoints());
    }

    /** Продление своего артефакта в неделю его слота: кэшбэк −1 % (минимум 3 %). */
    @Transactional
    public Purchase renew(String childId) {
        access.requireSelf(childId);
        PointAccount balance = points.lock(childId);
        ArtifactState state = state(childId);
        Instant now = Instant.now(clock);
        ArtifactRules.RenewOffer offer = ArtifactRules.renewOffer(state, owned.findByChildIdAndSlot(childId, state.getWeekSlot()), now)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTHING_TO_RENEW, "Продлевать нечего"));
        if (!balance.has(offer.price())) {
            throw new BusinessException(ErrorCode.POINTS, "Не хватает " + (offer.price() - balance.getPoints()) + " баллов");
        }
        PointAccount after = points.debit(childId, offer.price(), "Продление: " + offer.artifact().getName());
        OwnedArtifact own = offer.artifact();
        own.renew(offer.cashback(), state.purchaseActiveUntil());
        events.publishEvent(new ArtifactPurchased(UUID.randomUUID(), now, state.getFamilyId(), childId, own.getArtifactId(), own.getName(),
                offer.price(), own.getCashback(), own.getCategory(), true));
        return new Purchase(own, null, offer.price(), after.getPoints());
    }

    /** Для payment: баллы за покупку и лучший действующий кэшбэк на категорию. */
    @Transactional(readOnly = true)
    public Perks perks(String childId, String category, Money amount) {
        ArtifactState state = state(childId);
        access.requireChildOrParent(state.getFamilyId(), childId);
        Perks.Cashback cashback = ArtifactRules.cashbackFor(owned.findByChildId(childId), category, Instant.now(clock))
                .map(o -> new Perks.Cashback(o.getCashback(), o.getArtifactId(), o.getName()))
                .orElse(null);
        return new Perks(PointsService.forPurchase(amount.amount()), cashback);
    }

    /** Статистика: сколько кэшбэка принёс артефакт (событие CashbackGranted из payment). */
    @Transactional
    public void recordCashback(String childId, String artifactId, Money amount) {
        owned.findByChildIdAndArtifactId(childId, artifactId).ifPresent(o -> o.addCashback(amount));
    }

    private ArtifactState state(String childId) {
        return states.findById(childId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Ребёнок не найден"));
    }
}
