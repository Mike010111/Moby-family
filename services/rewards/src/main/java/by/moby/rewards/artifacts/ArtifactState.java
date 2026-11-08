package by.moby.rewards.artifacts;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Неделя цикла магазина у ребёнка: какой слот продаётся и что уже куплено на этой неделе
 * (купленный артефакт недоступен до конца недели).
 */
@Entity
@Table(name = "artifact_states")
public class ArtifactState {

    public static final Duration WEEK = Duration.ofDays(7);

    @Id
    private String childId;
    private String familyId;
    @Enumerated(EnumType.STRING)
    private Cohort cohort;
    @Convert(converter = SlotConverter.class)
    private Slot weekSlot;
    private Instant weekStart;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "artifact_sold_out", joinColumns = @JoinColumn(name = "child_id"))
    @Column(name = "artifact_id")
    private Set<String> soldOut = new LinkedHashSet<>();

    protected ArtifactState() {
    }

    public ArtifactState(String childId, String familyId, Cohort cohort, Slot weekSlot, Instant weekStart) {
        this.childId = childId;
        this.familyId = familyId;
        this.cohort = cohort;
        this.weekSlot = weekSlot;
        this.weekStart = weekStart;
    }

    public Instant weekEnd() {
        return weekStart.plus(WEEK);
    }

    public int cycleWeeks() {
        return Slot.cycle(cohort).size();
    }

    /** Артефакт, купленный сейчас, действует один цикл: до начала следующей недели своего слота. */
    public Instant purchaseActiveUntil() {
        return weekStart.plus(WEEK.multipliedBy(cycleWeeks()));
    }

    /** Начало ближайшей (следующей) недели слота относительно текущей недели цикла. */
    public Instant nextSlotWeekStart(Slot slot) {
        List<Slot> order = Slot.cycle(cohort);
        int cur = order.indexOf(weekSlot);
        int target = order.indexOf(slot);
        int diff = Math.floorMod(target - cur, order.size());
        return weekStart.plus(WEEK.multipliedBy(diff == 0 ? order.size() : diff));
    }

    void markSoldOut(String artifactId) {
        soldOut.add(artifactId);
    }

    public boolean isSoldOut(String artifactId) {
        return soldOut.contains(artifactId);
    }

    public String getChildId() {
        return childId;
    }

    public String getFamilyId() {
        return familyId;
    }

    public Cohort getCohort() {
        return cohort;
    }

    public Slot getWeekSlot() {
        return weekSlot;
    }

    public Instant getWeekStart() {
        return weekStart;
    }

    public Set<String> getSoldOut() {
        return Set.copyOf(soldOut);
    }
}
