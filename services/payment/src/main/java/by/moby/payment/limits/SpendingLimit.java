package by.moby.payment.limits;

import by.moby.common.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Лимит трат на календарный период. Пустой список категорий — все траты. */
@Entity
@Table(name = "spending_limits")
public class SpendingLimit {

    @Id
    private String id;
    private String accountId;
    @Column(name = "period_count")
    private int count;
    @Convert(converter = LimitUnitConverter.class)
    private LimitUnit unit;
    private Money amount;
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> categories = new ArrayList<>();
    private Instant startAt;

    protected SpendingLimit() {
    }

    public SpendingLimit(String id, String accountId, LimitDraft draft, Instant startAt) {
        this.id = id == null ? UUID.randomUUID().toString() : id;
        this.accountId = accountId;
        apply(draft);
        this.startAt = startAt;
    }

    /** Возвращает true, если поменялся срок — тогда период начинается заново. */
    public boolean update(LimitDraft draft) {
        boolean periodChanged = draft.count() != count || draft.unit() != unit;
        apply(draft);
        return periodChanged;
    }

    public void restartAt(Instant start) {
        this.startAt = start;
    }

    private void apply(LimitDraft draft) {
        this.count = draft.count();
        this.unit = draft.unit();
        this.amount = draft.amount();
        this.categories = new ArrayList<>(draft.categories());
    }

    /** Длина срока в днях (месяц — 30 дней) — для иерархии. */
    public int periodDays() {
        return count * unit.days();
    }

    public String getId() {
        return id;
    }

    public String getAccountId() {
        return accountId;
    }

    public int getCount() {
        return count;
    }

    public LimitUnit getUnit() {
        return unit;
    }

    public Money getAmount() {
        return amount;
    }

    public List<String> getCategories() {
        return List.copyOf(categories);
    }

    public Instant getStartAt() {
        return startAt;
    }
}
