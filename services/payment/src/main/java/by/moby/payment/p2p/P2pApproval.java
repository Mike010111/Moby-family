package by.moby.payment.p2p;

import by.moby.common.money.Money;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "p2p_approvals")
public class P2pApproval {

    @Id
    private String id;
    private String accountId;
    private String requestId;
    private Money maxAmount;
    @Enumerated(EnumType.STRING)
    private ApprovalStatus status;
    private Money usedAmount;
    private Instant createdAt;

    protected P2pApproval() {
    }

    P2pApproval(String accountId, String requestId, Money maxAmount, Instant createdAt) {
        this.id = UUID.randomUUID().toString();
        this.accountId = accountId;
        this.requestId = requestId;
        this.maxAmount = maxAmount;
        this.status = ApprovalStatus.ACTIVE;
        this.createdAt = createdAt;
    }

    void use(Money amount) {
        if (status != ApprovalStatus.ACTIVE) {
            throw new IllegalStateException("Approval " + id + " is " + status);
        }
        status = ApprovalStatus.USED;
        usedAmount = amount;
    }

    void replace() {
        if (status == ApprovalStatus.ACTIVE) {
            status = ApprovalStatus.REPLACED;
        }
    }

    public boolean covers(Money amount) {
        return status == ApprovalStatus.ACTIVE && !amount.isGreaterThan(maxAmount);
    }

    public String getId() {
        return id;
    }

    public String getRequestId() {
        return requestId;
    }

    public Money getMaxAmount() {
        return maxAmount;
    }

    public ApprovalStatus getStatus() {
        return status;
    }

    public Money getUsedAmount() {
        return usedAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
