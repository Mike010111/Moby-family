package by.moby.family.requests;

import by.moby.common.events.RequestKind;
import by.moby.common.money.Money;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/** Запрос ребёнка к родственнику: пополнение, перевод сверх лимита, изменение / удаление копилки. */
@Entity
@Table(name = "requests")
public class ChildRequest {

    @Id
    private String id;
    private String familyId;
    private String childId;
    private String relativeId;
    @Enumerated(EnumType.STRING)
    private RequestKind kind;
    private Money amount;
    private String reason;
    private String contactName;
    private String piggyId;
    @Enumerated(EnumType.STRING)
    private RequestStatus status;
    private String resolution;
    private Instant createdAt;
    private Instant resolvedAt;
    @Version
    private long version;

    protected ChildRequest() {
    }

    ChildRequest(String familyId, String childId, String relativeId, RequestKind kind, Money amount, String reason,
                 RequestStatus initial, Instant createdAt) {
        if (initial != RequestStatus.PENDING && initial != RequestStatus.SENT) {
            throw new IllegalArgumentException("Request starts as PENDING or SENT, not " + initial);
        }
        this.id = UUID.randomUUID().toString();
        this.familyId = familyId;
        this.childId = childId;
        this.relativeId = relativeId;
        this.kind = kind;
        this.amount = amount;
        this.reason = reason;
        this.status = initial;
        this.createdAt = createdAt;
    }

    ChildRequest withContact(String contactName) {
        this.contactName = contactName;
        return this;
    }

    ChildRequest withPiggy(String piggyId) {
        this.piggyId = piggyId;
        return this;
    }

    void approve(Instant at) {
        moveTo(RequestStatus.APPROVED, at, null);
    }

    void decline(Instant at) {
        moveTo(RequestStatus.DECLINED, at, null);
    }

    void cancel(Instant at, String resolution) {
        moveTo(RequestStatus.CANCELLED, at, resolution);
    }

    private void moveTo(RequestStatus next, Instant at, String resolution) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("Request " + id + ": " + status + " → " + next + " is not allowed");
        }
        this.status = next;
        this.resolvedAt = at;
        this.resolution = resolution;
    }

    public boolean isPending() {
        return status == RequestStatus.PENDING;
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

    public String getRelativeId() {
        return relativeId;
    }

    public RequestKind getKind() {
        return kind;
    }

    public Money getAmount() {
        return amount;
    }

    public String getReason() {
        return reason;
    }

    public String getContactName() {
        return contactName;
    }

    public String getPiggyId() {
        return piggyId;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public String getResolution() {
        return resolution;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}
