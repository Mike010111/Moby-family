package by.moby.rewards.points;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Баллы участника семьи. Баллы ребёнка изолированы от родительских. */
@Entity
@Table(name = "point_accounts")
public class PointAccount {

    @Id
    private String memberId;
    private String familyId;
    private int points;
    @Version
    private long version;

    protected PointAccount() {
    }

    public PointAccount(String memberId, String familyId, int points) {
        this.memberId = memberId;
        this.familyId = familyId;
        this.points = points;
    }

    void add(int delta) {
        if (points + delta < 0) {
            throw new IllegalStateException("Points of " + memberId + " would become negative");
        }
        points += delta;
    }

    public boolean has(int amount) {
        return points >= amount;
    }

    public String getMemberId() {
        return memberId;
    }

    public String getFamilyId() {
        return familyId;
    }

    public int getPoints() {
        return points;
    }
}
