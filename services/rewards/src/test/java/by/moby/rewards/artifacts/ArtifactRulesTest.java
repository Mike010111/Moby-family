package by.moby.rewards.artifacts;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Аналог теста «Продление…» и правил предложений из store.test.js — без БД. */
class ArtifactRulesTest {

    static final Instant WEEK_START = Instant.parse("2026-10-18T21:00:00Z"); // понедельник 00:00 по Минску
    static final Instant NOW = WEEK_START.plus(Duration.ofDays(2));

    final ArtifactState state = new ArtifactState("misha", "fam", Cohort.JUNIOR, Slot.HANDS, WEEK_START);
    final ArtifactCatalog.Artifact diamond = ArtifactCatalog.byId("diamond").orElseThrow();

    @Test
    void renewOnlyExpiredArtifactOfTheWeekSlotMinusOnePercentDownToThree() {
        OwnedArtifact own = new OwnedArtifact("misha", diamond, Cohort.JUNIOR, NOW, state.purchaseActiveUntil());
        assertThat(ArtifactRules.renewOffer(state, Optional.of(own), NOW)).as("пока действует — продлевать нечего").isEmpty();

        OwnedArtifact expired = new OwnedArtifact("misha", diamond, Cohort.JUNIOR, NOW.minus(Duration.ofDays(28)), NOW.minusSeconds(1));
        var offer = ArtifactRules.renewOffer(state, Optional.of(expired), NOW).orElseThrow();
        assertThat(List.of(offer.cashback(), offer.price())).containsExactly(4, 100);
        assertThat(offer.rarity()).isEqualTo(Rarity.RARE);

        expired.renew(3, NOW.minusSeconds(1));
        var minimal = ArtifactRules.renewOffer(state, Optional.of(expired), NOW).orElseThrow();
        assertThat(List.of(minimal.cashback(), minimal.price())).containsExactly(3, 50);
        assertThat(ArtifactRules.renewOffer(state, Optional.of(expired), state.weekEnd())).as("неделя слота закончилась").isEmpty();
    }

    @Test
    void cycleLengthAndNextSlotWeek() {
        assertThat(state.cycleWeeks()).isEqualTo(4);
        assertThat(state.nextSlotWeekStart(Slot.GLASSES)).isEqualTo(WEEK_START.plus(Duration.ofDays(14)));
        assertThat(state.nextSlotWeekStart(Slot.HANDS)).isEqualTo(WEEK_START.plus(Duration.ofDays(28)));
        ArtifactState teen = new ArtifactState("ivan", "fam", Cohort.TEEN, Slot.HANDS, WEEK_START);
        assertThat(teen.cycleWeeks()).isEqualTo(5);
        assertThat(teen.nextSlotWeekStart(Slot.OUTER)).isEqualTo(WEEK_START.plus(Duration.ofDays(28)));
    }

    @Test
    void offerStatuses() {
        OwnedArtifact own = new OwnedArtifact("misha", diamond, Cohort.JUNIOR, NOW, state.purchaseActiveUntil());
        assertThat(ArtifactRules.offer(state, Optional.of(own), 500, diamond, NOW).status()).isEqualTo(ArtifactRules.OfferStatus.EQUIPPED);
        var coin = ArtifactCatalog.byId("coin").orElseThrow();
        var offer = ArtifactRules.offer(state, Optional.of(own), 500, coin, NOW);
        assertThat(offer.status()).isEqualTo(ArtifactRules.OfferStatus.AVAILABLE);
        assertThat(offer.replaces()).isSameAs(own);
        assertThat(ArtifactRules.offer(state, Optional.empty(), 30, coin, NOW).lack()).isEqualTo(70);
    }

    @Test
    void bestActiveCashbackForCategory() {
        var nut = new OwnedArtifact("misha", ArtifactCatalog.byId("nut").orElseThrow(), Cohort.JUNIOR, NOW, NOW.plusSeconds(60));
        var expired = new OwnedArtifact("misha", diamond, Cohort.JUNIOR, NOW, NOW.minusSeconds(1));
        assertThat(ArtifactRules.cashbackFor(List.of(nut, expired), "food", NOW)).contains(nut);
        assertThat(ArtifactRules.cashbackFor(List.of(nut, expired), "cafe", NOW)).isEmpty();
    }
}
