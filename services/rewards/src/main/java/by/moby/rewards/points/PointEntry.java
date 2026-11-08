package by.moby.rewards.points;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Журнал начислений и списаний: баланс баллов объясним по записям. */
@Entity
@Table(name = "point_entries")
public class PointEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String memberId;
    private int delta;
    private String reason;
    private Instant createdAt;

    protected PointEntry() {
    }

    PointEntry(String memberId, int delta, String reason, Instant createdAt) {
        this.memberId = memberId;
        this.delta = delta;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public int getDelta() {
        return delta;
    }

    public String getReason() {
        return reason;
    }
}
