package by.moby.payment.account;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Проводка двойной записи: набор записей по счетам с нулевой суммой. */
@Entity
@Table(name = "ledger_transactions")
public class LedgerTransaction {

    @Id
    private UUID id;
    @Enumerated(EnumType.STRING)
    private LedgerKind kind;
    private String description;
    private Instant createdAt;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL)
    private List<LedgerEntry> entries = new ArrayList<>();

    protected LedgerTransaction() {
    }

    LedgerTransaction(LedgerKind kind, String description, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.kind = kind;
        this.description = description;
        this.createdAt = createdAt;
    }

    void add(LedgerEntry entry) {
        entries.add(entry);
    }

    public UUID getId() {
        return id;
    }

    public LedgerKind getKind() {
        return kind;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<LedgerEntry> getEntries() {
        return List.copyOf(entries);
    }
}
