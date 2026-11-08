package by.moby.rewards.artifacts;

import by.moby.common.error.BusinessException;
import by.moby.common.error.ErrorCode;
import by.moby.common.money.Money;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/rewards/children/{childId}")
class ArtifactController {

    record PurchaseView(ChildRewardsView.Owned artifact, String replaced, int price, int points) {
    }

    private final ArtifactService artifacts;

    ArtifactController(ArtifactService artifacts) {
        this.artifacts = artifacts;
    }

    @GetMapping
    ChildRewardsView view(@PathVariable String childId) {
        ArtifactService.Snapshot s = artifacts.snapshot(childId);
        ArtifactState state = s.state();
        Map<Slot, OwnedArtifact> bySlot = new LinkedHashMap<>();
        s.owned().forEach(o -> bySlot.put(o.getSlot(), o));
        Map<String, ChildRewardsView.Owned> owned = new LinkedHashMap<>();
        for (Slot slot : Slot.cycle(state.getCohort())) {
            owned.put(slot.key(), Optional.ofNullable(bySlot.get(slot)).map(o -> owned(o, s)).orElse(null));
        }
        List<ChildRewardsView.ShopItem> shop = ArtifactCatalog.shopFor(state.getWeekSlot()).stream()
                .map(id -> ArtifactCatalog.byId(id).orElseThrow())
                .map(a -> {
                    ArtifactRules.Offer offer = ArtifactRules.offer(state, Optional.ofNullable(bySlot.get(a.slot())), s.points(), a, s.now());
                    return new ChildRewardsView.ShopItem(a.id(), a.name(), a.slot().key(), a.rarity().key(), a.rarity().cashback(),
                            a.categoryFor(state.getCohort()), offer.price(), offer.status().name().toLowerCase(), offer.lack(),
                            offer.replaces() == null ? null : offer.replaces().getName());
                })
                .toList();
        ChildRewardsView.Renew renew = ArtifactRules.renewOffer(state, Optional.ofNullable(bySlot.get(state.getWeekSlot())), s.now())
                .map(r -> new ChildRewardsView.Renew(r.artifact().getArtifactId(), r.artifact().getName(), r.cashback(), r.rarity().key(), r.price()))
                .orElse(null);
        return new ChildRewardsView(childId, state.getCohort().name(), s.points(), state.getWeekSlot().key(), state.getWeekStart(),
                state.weekEnd(), Slot.cycle(state.getCohort()).stream().map(Slot::key).toList(), owned,
                List.copyOf(state.getSoldOut()), shop, renew);
    }

    @PostMapping("/artifacts/{artifactId}/purchase")
    PurchaseView buy(@PathVariable String childId, @PathVariable String artifactId) {
        ArtifactService.Purchase p = artifacts.buy(childId, artifactId);
        return new PurchaseView(owned(p.artifact(), null), p.replaced(), p.price(), p.points());
    }

    @PostMapping("/artifacts/renew")
    PurchaseView renew(@PathVariable String childId) {
        ArtifactService.Purchase p = artifacts.renew(childId);
        return new PurchaseView(owned(p.artifact(), null), null, p.price(), p.points());
    }

    /** Для payment: баллы и кэшбэк за покупку в категории. */
    @GetMapping("/payment-perks")
    ArtifactService.Perks perks(@PathVariable String childId, @RequestParam String category, @RequestParam BigDecimal amount) {
        if (amount.signum() <= 0 || amount.scale() > 2) {
            throw new BusinessException(ErrorCode.AMOUNT, "Сумма покупки должна быть положительной, не больше двух знаков");
        }
        return artifacts.perks(childId, category, Money.of(amount));
    }

    private static ChildRewardsView.Owned owned(OwnedArtifact o, ArtifactService.Snapshot s) {
        boolean active = s == null || o.isActive(s.now());
        return new ChildRewardsView.Owned(o.getArtifactId(), o.getSlot().key(), o.getName(), o.getRarity().key(), o.getCategory(),
                o.getCashback(), o.getCycles(), o.getBoughtAt(), o.getActiveUntil(), active, o.getCashbackEarned());
    }
}
