package by.moby.payment.subscriptions;

import by.moby.common.money.Money;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Подписка подростка (стриминг, курсы) — списывается с карты учащегося. */
@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    private String id;
    private String accountId;
    private String title;
    private Money price;
    private String nextLabel;
    private boolean active;
    private String icon;
    private int position;

    protected Subscription() {
    }

    public Subscription(String id, String accountId, String title, Money price, String nextLabel, boolean active, String icon, int position) {
        this.id = id;
        this.accountId = accountId;
        this.title = title;
        this.price = price;
        this.nextLabel = nextLabel;
        this.active = active;
        this.icon = icon;
        this.position = position;
    }

    void setActive(boolean active) {
        this.active = active;
    }

    void changePrice(Money price) {
        this.price = price;
    }

    public String getId() {
        return id;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getTitle() {
        return title;
    }

    public Money getPrice() {
        return price;
    }

    public String getNextLabel() {
        return nextLabel;
    }

    public boolean isActive() {
        return active;
    }

    public String getIcon() {
        return icon;
    }
}
