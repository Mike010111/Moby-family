package by.moby.family.tasks;

import by.moby.common.money.Money;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "tasks")
public class Task {

    @Id
    private String id;
    private String familyId;
    private String childId;
    private String title;
    private Money reward;
    @Enumerated(EnumType.STRING)
    private TaskStatus status;
    private Instant createdAt;
    @Version
    private long version;

    protected Task() {
    }

    public Task(String id, String familyId, String childId, String title, Money reward, TaskStatus status, Instant createdAt) {
        this.id = id;
        this.familyId = familyId;
        this.childId = childId;
        this.title = title;
        this.reward = reward;
        this.status = status;
        this.createdAt = createdAt;
    }

    boolean canMoveTo(TaskStatus next) {
        return status.canMoveTo(next);
    }

    void moveTo(TaskStatus next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("Task " + id + ": " + status + " → " + next + " is not allowed");
        }
        this.status = next;
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

    public String getTitle() {
        return title;
    }

    public Money getReward() {
        return reward;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
