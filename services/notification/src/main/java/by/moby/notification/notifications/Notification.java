package by.moby.notification.notifications;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Элемент ленты родителя ({@code kind}: info / alert / request / task) или уведомление ребёнку
 * ({@code tone}: ok / error / info), которое покажется тостом при открытии его экрана.
 */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    private String id;
    private String familyId;
    @Enumerated(EnumType.STRING)
    private Audience audience;
    private String childId;
    private String kind;
    private String tone;
    private String title;
    private String text;
    private String requestId;
    private String taskId;
    private Instant createdAt;
    private boolean seen;

    protected Notification() {
    }

    private Notification(String familyId, Audience audience, String childId, Instant createdAt) {
        this.id = UUID.randomUUID().toString();
        this.familyId = familyId;
        this.audience = audience;
        this.childId = childId;
        this.createdAt = createdAt;
    }

    public static Notification feed(String familyId, String childId, String kind, String title, String text, Instant at) {
        Notification n = new Notification(familyId, Audience.FEED, childId, at);
        n.kind = kind;
        n.title = title;
        n.text = text;
        return n;
    }

    public static Notification inbox(String familyId, String childId, String text, String tone, Instant at) {
        Notification n = new Notification(familyId, Audience.INBOX, childId, at);
        n.text = text;
        n.tone = tone;
        return n;
    }

    public Notification withRequest(String requestId) {
        this.requestId = requestId;
        return this;
    }

    public Notification withTask(String taskId) {
        this.taskId = taskId;
        return this;
    }

    void markSeen() {
        this.seen = true;
    }

    public String getId() {
        return id;
    }

    public String getFamilyId() {
        return familyId;
    }

    public Audience getAudience() {
        return audience;
    }

    public String getChildId() {
        return childId;
    }

    public String getKind() {
        return kind;
    }

    public String getTone() {
        return tone;
    }

    public String getTitle() {
        return title;
    }

    public String getText() {
        return text;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getTaskId() {
        return taskId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isSeen() {
        return seen;
    }
}
