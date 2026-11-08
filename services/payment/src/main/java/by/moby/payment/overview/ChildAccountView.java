package by.moby.payment.overview;

import by.moby.common.money.Money;
import by.moby.payment.limits.api.LimitView;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Всё, что экран ребёнка и хаб родителя показывают о счёте. */
public record ChildAccountView(
        String childId,
        String familyId,
        String name,
        String cohort,
        Money balance,
        boolean frozen,
        Card card,
        Map<String, Boolean> allow,
        List<LimitView> limits,
        Autopay autopay,
        List<SubscriptionItem> subscriptions,
        List<Approval> approvals,
        Stats stats) {

    public record Card(String title, String mask, String number, String validThru, String studentId, String holder, String image) {
    }

    public record Autopay(boolean enabled, Money amount, String every, int weekday, int monthDay) {
    }

    public record SubscriptionItem(String id, String title, Money price, String next, boolean active, String icon) {
    }

    public record Approval(String id, String requestId, Money max, String status, Money usedAmount, Instant createdAt) {
    }

    public record Stats(Integer txCount, String since) {
    }
}
