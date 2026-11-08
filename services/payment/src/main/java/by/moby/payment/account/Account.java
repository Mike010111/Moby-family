package by.moby.payment.account;

import by.moby.common.money.Money;
import by.moby.payment.catalog.Cohort;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Счёт. Баланс меняется только через {@link Ledger} — вместе с проводками двойной записи.
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    private String id;
    private String familyId;
    private String ownerId;
    @Enumerated(EnumType.STRING)
    private AccountKind kind;
    @Enumerated(EnumType.STRING)
    private Cohort cohort;
    @Embedded
    private Holder holder;
    private Money balance;
    private boolean frozen;
    @Embedded
    private CardDetails card;
    private String packageName;
    private String externalRef;
    private Integer statsTxCount;
    private String statsSince;

    /** Тумблеры категорий: ключ есть — категория управляется родителем; значение — разрешена ли. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "category_permissions", joinColumns = @JoinColumn(name = "account_id"))
    @MapKeyColumn(name = "category_key")
    @Column(name = "allowed")
    private Map<String, Boolean> permissions = new LinkedHashMap<>();

    @Version
    private long version;

    protected Account() {
    }

    private Account(String id, String familyId, String ownerId, AccountKind kind) {
        this.id = Objects.requireNonNull(id);
        this.familyId = familyId;
        this.ownerId = Objects.requireNonNull(ownerId);
        this.kind = Objects.requireNonNull(kind);
        this.balance = Money.ZERO;
    }

    public static Account parent(String id, String familyId, String ownerId, Holder holder, String cardMask, String packageName) {
        Account a = new Account(id, familyId, ownerId, AccountKind.PARENT);
        a.holder = holder;
        a.card = new CardDetails(null, cardMask, null, null, null, null, null);
        a.packageName = packageName;
        return a;
    }

    public static Account child(String id, String familyId, String childId, Cohort cohort, Holder holder, CardDetails card) {
        Account a = new Account(id, familyId, childId, AccountKind.CHILD);
        a.cohort = Objects.requireNonNull(cohort);
        a.holder = holder;
        a.card = card;
        return a;
    }

    public static Account savings(String id, String familyId, String childId, String piggyId) {
        Account a = new Account(id, familyId, childId, AccountKind.SAVINGS);
        a.holder = new Holder(null, null, null, null, false);
        a.externalRef = Objects.requireNonNull(piggyId);
        return a;
    }

    /** Применяется только из {@link Ledger#post}. */
    void apply(Money delta) {
        Money next = balance.plus(delta);
        if (next.isNegative()) {
            throw new IllegalStateException("Account " + id + " would become negative");
        }
        balance = next;
    }

    public boolean freeze() {
        boolean changed = !frozen;
        frozen = true;
        return changed;
    }

    public boolean unfreeze() {
        boolean changed = frozen;
        frozen = false;
        return changed;
    }

    /** Категория под управлением родителя и выключена (условие PARENT_CATEGORY в checkPayment). */
    public boolean isDenied(String categoryKey) {
        return permissions.containsKey(categoryKey) && !permissions.get(categoryKey);
    }

    public boolean isAllowed(String categoryKey) {
        return Boolean.TRUE.equals(permissions.get(categoryKey));
    }

    public void setPermission(String categoryKey, boolean allowed) {
        permissions.put(categoryKey, allowed);
    }

    public void setStats(int txCount, String since) {
        this.statsTxCount = txCount;
        this.statsSince = since;
    }

    public boolean hasFunds(Money amount) {
        return !amount.isGreaterThan(balance);
    }

    public String getId() {
        return id;
    }

    public String getFamilyId() {
        return familyId;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public AccountKind getKind() {
        return kind;
    }

    public Cohort getCohort() {
        return cohort;
    }

    public Holder getHolder() {
        return holder;
    }

    public Money getBalance() {
        return balance;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public CardDetails getCard() {
        return card;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getExternalRef() {
        return externalRef;
    }

    public Integer getStatsTxCount() {
        return statsTxCount;
    }

    public String getStatsSince() {
        return statsSince;
    }

    public Map<String, Boolean> getPermissions() {
        return Map.copyOf(permissions);
    }
}
