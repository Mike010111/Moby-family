package by.moby.rewards.artifacts;

import by.moby.common.money.Money;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Экземпляр артефакта у ребёнка — снимок со своим кэшбэком и сроком действия. Один на слот. */
@Entity
@Table(name = "owned_artifacts")
public class OwnedArtifact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String childId;
    @Convert(converter = SlotConverter.class)
    private Slot slot;
    private String artifactId;
    private String name;
    @Enumerated(EnumType.STRING)
    private Rarity rarity;
    private String category;
    private int cashback;
    private int cycles;
    private Instant boughtAt;
    private Instant activeUntil;
    private Money cashbackEarned;

    protected OwnedArtifact() {
    }

    /** {@code makeOwned}: кэшбэк — по редкости артефакта. */
    public OwnedArtifact(String childId, ArtifactCatalog.Artifact artifact, Cohort cohort, Instant boughtAt, Instant activeUntil) {
        this.childId = childId;
        this.slot = artifact.slot();
        assign(artifact, cohort, boughtAt, activeUntil);
    }

    /** Новая покупка в тот же слот заменяет прежний артефакт. */
    void replaceWith(ArtifactCatalog.Artifact artifact, Cohort cohort, Instant boughtAt, Instant activeUntil) {
        assign(artifact, cohort, boughtAt, activeUntil);
    }

    private void assign(ArtifactCatalog.Artifact artifact, Cohort cohort, Instant boughtAt, Instant activeUntil) {
        this.artifactId = artifact.id();
        this.name = artifact.name();
        this.rarity = artifact.rarity();
        this.category = artifact.categoryFor(cohort);
        this.cashback = artifact.rarity().cashback();
        this.cycles = 1;
        this.boughtAt = boughtAt;
        this.activeUntil = activeUntil;
        this.cashbackEarned = Money.ZERO;
    }

    void renew(int cashback, Instant activeUntil) {
        this.cashback = cashback;
        this.cycles += 1;
        this.activeUntil = activeUntil;
    }

    void addCashback(Money amount) {
        this.cashbackEarned = cashbackEarned.plus(amount);
    }

    public boolean isActive(Instant now) {
        return activeUntil.isAfter(now);
    }

    public String getChildId() {
        return childId;
    }

    public Slot getSlot() {
        return slot;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getName() {
        return name;
    }

    public Rarity getRarity() {
        return rarity;
    }

    public String getCategory() {
        return category;
    }

    public int getCashback() {
        return cashback;
    }

    public int getCycles() {
        return cycles;
    }

    public Instant getBoughtAt() {
        return boughtAt;
    }

    public Instant getActiveUntil() {
        return activeUntil;
    }

    public Money getCashbackEarned() {
        return cashbackEarned;
    }
}
