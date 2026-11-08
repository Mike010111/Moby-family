package by.moby.notification.notifications;

import by.moby.common.events.AccountFrozen;
import by.moby.common.events.AccountUnfrozen;
import by.moby.common.events.Actor;
import by.moby.common.events.PaymentCompleted;
import by.moby.common.events.PaymentDeclined;
import by.moby.common.events.PiggyCompleted;
import by.moby.common.events.RequestCreated;
import by.moby.common.events.RequestKind;
import by.moby.common.events.RequestResolved;
import by.moby.common.events.TaskApproved;
import by.moby.common.events.TaskSubmitted;

/** Тексты уведомлений — дословно как {@code notify} / {@code pushFeed} в actions.js. */
public final class NotificationTexts {

    public record Feed(String kind, String title, String text) {
    }

    public record Inbox(String text, String tone) {
    }

    private NotificationTexts() {
    }

    private static String verb(Actor actor, String stem) {
        return actor.label() + " " + (actor.female() ? stem + "а" : stem);
    }

    public static Feed requestCreated(RequestCreated e) {
        return switch (e.kind()) {
            case TOPUP -> new Feed("request", e.childName() + " просит " + e.amount().format(), e.reason());
            case P2P_OVER -> new Feed("request", e.childName() + " просит разрешить перевод " + e.amount().format() + " сверх лимита",
                    "Получатель: " + e.contactName() + " · по лимиту доступно " + e.limitRemaining().format());
            case PIGGY_UPDATE, PIGGY_DELETE -> new Feed("request",
                    e.childName() + ": " + Character.toLowerCase(e.reason().charAt(0)) + e.reason().substring(1),
                    e.kind() == RequestKind.PIGGY_DELETE
                            ? "Накоплено " + e.piggySaved().format() + " — вернётся на счёт ребёнка"
                            : "Накоплено " + e.piggySaved().format() + " из " + e.piggyTarget().format());
        };
    }

    public static Inbox requestResolved(RequestResolved e) {
        if (!e.approved()) {
            String what = switch (e.kind()) {
                case P2P_OVER -> "перевод " + e.amount().format() + " сверх лимита";
                case PIGGY_UPDATE, PIGGY_DELETE -> e.reason().toLowerCase();
                case TOPUP -> e.amount().format() + " · " + e.reason();
            };
            return new Inbox(verb(e.resolver(), "отклонил") + " запрос: " + what, "error");
        }
        String text = switch (e.kind()) {
            case TOPUP -> verb(e.resolver(), "одобрил") + " запрос: +" + e.amount().format();
            case P2P_OVER -> verb(e.resolver(), "одобрил") + " разовый перевод сверх лимита до " + e.amount().format();
            case PIGGY_DELETE -> verb(e.resolver(), "разрешил") + " удалить копилку «" + e.piggyGoal() + "»: " + e.returned().format() + " вернулись на счёт";
            case PIGGY_UPDATE -> verb(e.resolver(), "одобрил") + " новую цель «" + e.piggyGoal() + "»: " + e.amount().format();
        };
        return new Inbox(text, "ok");
    }

    public static Feed taskSubmitted(TaskSubmitted e) {
        return new Feed("task", e.childName() + ": задание выполнено", e.title() + " · награда " + e.reward().format());
    }

    public static Inbox taskApproved(TaskApproved e) {
        return new Inbox("Задание «" + e.title() + "» принято: +" + e.reward().format() + " и " + e.points() + " баллов", "ok");
    }

    public static Inbox piggyCompletedInbox(PiggyCompleted e) {
        return new Inbox("Копилка «" + e.goal() + "» собрана! " + e.amount().format() + " зачислены на счёт", "ok");
    }

    public static Feed piggyCompletedFeed(PiggyCompleted e) {
        return new Feed("info", e.childName() + ": копилка «" + e.goal() + "» собрана", e.amount().format() + " зачислены на счёт ребёнка");
    }

    public static Feed frozen(AccountFrozen e) {
        return new Feed("alert", "Счёт " + e.childName() + " заблокирован", e.cardTitle() + " " + e.cardMask());
    }

    public static Inbox frozenInbox(AccountFrozen e) {
        return new Inbox(verb(e.actor(), "заблокировал") + " счёт", "error");
    }

    public static Feed unfrozen(AccountUnfrozen e) {
        return new Feed("info", "Счёт " + e.childName() + " разблокирован", e.cardTitle() + " " + e.cardMask());
    }

    public static Inbox unfrozenInbox(AccountUnfrozen e) {
        return new Inbox(verb(e.actor(), "разблокировал") + " счёт", "ok");
    }

    public static Feed paymentDeclined(PaymentDeclined e) {
        return new Feed("alert", "Отклонена оплата: " + e.merchantName(), e.amount().format() + " · " + e.reason());
    }

    public static Feed paymentCompleted(PaymentCompleted e) {
        return new Feed("info", e.childName() + " " + (e.childFemale() ? "оплатила" : "оплатил") + " «" + e.merchantName() + "»",
                e.amount().format() + " · " + e.categoryLabel());
    }
}
