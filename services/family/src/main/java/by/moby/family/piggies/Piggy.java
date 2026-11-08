package by.moby.family.piggies;

import by.moby.common.money.Money;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * Копилка-цель. Деньги лежат на SAVINGS-счёте в payment; {@code saved} — подтверждённый остаток
 * из ответа payment после каждой операции.
 */
@Entity
@Table(name = "piggies")
public class Piggy {

    @Id
    private String id;
    private String familyId;
    private String childId;
    private String goal;
    private Money target;
    private Money saved;
    @Enumerated(EnumType.STRING)
    private PiggyStatus status;
    private Instant createdAt;
    @Version
    private long version;

    protected Piggy() {
    }

    public Piggy(String id, String familyId, String childId, String goal, Money target, Money saved, Instant createdAt) {
        this.id = id;
        this.familyId = familyId;
        this.childId = childId;
        this.goal = goal;
        this.target = target;
        this.saved = saved;
        this.status = PiggyStatus.ACTIVE;
        this.createdAt = createdAt;
    }

    Money room() {
        return target.minus(saved);
    }

    boolean isReached() {
        return !saved.isLessThan(target);
    }

    void recordSaved(Money saved) {
        this.saved = saved;
    }

    void changeTarget(Money target) {
        this.target = target;
    }

    void complete() {
        close(PiggyStatus.COMPLETED);
    }

    void closeAndReturn() {
        close(PiggyStatus.CLOSED);
    }

    private void close(PiggyStatus next) {
        if (status != PiggyStatus.ACTIVE) {
            throw new IllegalStateException("Piggy " + id + " is already " + status);
        }
        this.status = next;
        this.saved = Money.ZERO;
    }

    public String getId() {
        return id;
    }

    public String getFamilyId() {
        return familyId;
    }

    public String getChildId() {
        return childId;
    }

    public String getGoal() {
        return goal;
    }

    public Money getTarget() {
        return target;
    }

    public Money getSaved() {
        return saved;
    }

    public PiggyStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
