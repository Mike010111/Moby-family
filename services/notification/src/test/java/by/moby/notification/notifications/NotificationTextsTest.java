package by.moby.notification.notifications;

import by.moby.common.error.ErrorCode;
import by.moby.common.events.AccountFrozen;
import by.moby.common.events.Actor;
import by.moby.common.events.PaymentCompleted;
import by.moby.common.events.PaymentDeclined;
import by.moby.common.events.PiggyCompleted;
import by.moby.common.events.RequestCreated;
import by.moby.common.events.RequestKind;
import by.moby.common.events.RequestResolved;
import by.moby.common.events.TaskApproved;
import by.moby.common.events.TaskSubmitted;
import by.moby.common.money.Money;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Тексты уведомлений совпадают с actions.js (на них опираются проверки store.test.js). */
class NotificationTextsTest {

    static final Actor MOM = new Actor("mom", "Мама", true);
    static final Actor DAD = new Actor("dad", "Папа", false);

    static RequestResolved resolved(RequestKind kind, boolean approved, String amount, String reason, Actor who, String goal, String returned) {
        return new RequestResolved(UUID.randomUUID(), Instant.now(), "f", "misha", "r", kind, approved, Money.of(amount), reason, who, goal,
                returned == null ? null : Money.of(returned));
    }

    @Test
    void requestTexts() {
        var topup = new RequestCreated(UUID.randomUUID(), Instant.now(), "f", "misha", "Миша", "r", RequestKind.TOPUP, "mom", true,
                Money.of("5"), "На обед", null, null, null, null, null);
        assertThat(NotificationTexts.requestCreated(topup)).isEqualTo(new NotificationTexts.Feed("request", "Миша просит 5,00 BYN", "На обед"));
        var p2p = new RequestCreated(UUID.randomUUID(), Instant.now(), "f", "ivan", "Иван", "r", RequestKind.P2P_OVER, "mom", true,
                Money.of("35"), "Перевод: Катя Соколова", "Катя Соколова", Money.of("24.50"), null, null, null);
        assertThat(NotificationTexts.requestCreated(p2p).title()).isEqualTo("Иван просит разрешить перевод 35,00 BYN сверх лимита");
        assertThat(NotificationTexts.requestCreated(p2p).text()).isEqualTo("Получатель: Катя Соколова · по лимиту доступно 24,50 BYN");
        var piggy = new RequestCreated(UUID.randomUUID(), Instant.now(), "f", "misha", "Миша", "r", RequestKind.PIGGY_UPDATE, "mom", true,
                Money.of("300"), "Изменить цель «Велосипед»: 250,00 BYN → 300,00 BYN", null, null, "Велосипед", Money.of("86.50"), Money.of("250"));
        assertThat(NotificationTexts.requestCreated(piggy).title()).isEqualTo("Миша: изменить цель «Велосипед»: 250,00 BYN → 300,00 BYN");
        assertThat(NotificationTexts.requestCreated(piggy).text()).isEqualTo("Накоплено 86,50 BYN из 250,00 BYN");
    }

    @Test
    void resolutionTextsAgreeWithRelativeGender() {
        assertThat(NotificationTexts.requestResolved(resolved(RequestKind.TOPUP, true, "5", "На обед", MOM, null, null)).text())
                .isEqualTo("Мама одобрила запрос: +5,00 BYN");
        assertThat(NotificationTexts.requestResolved(resolved(RequestKind.TOPUP, true, "5", "На обед", DAD, null, null)).text())
                .isEqualTo("Папа одобрил запрос: +5,00 BYN");
        assertThat(NotificationTexts.requestResolved(resolved(RequestKind.P2P_OVER, true, "35", "x", MOM, null, null)).text())
                .isEqualTo("Мама одобрила разовый перевод сверх лимита до 35,00 BYN");
        assertThat(NotificationTexts.requestResolved(resolved(RequestKind.PIGGY_DELETE, true, "86.50", "x", MOM, "Велосипед", "86.50")).text())
                .isEqualTo("Мама разрешила удалить копилку «Велосипед»: 86,50 BYN вернулись на счёт");
        assertThat(NotificationTexts.requestResolved(resolved(RequestKind.PIGGY_UPDATE, true, "300", "x", MOM, "Велосипед", null)).text())
                .isEqualTo("Мама одобрила новую цель «Велосипед»: 300,00 BYN");
        var declined = NotificationTexts.requestResolved(resolved(RequestKind.TOPUP, false, "5", "На обед", MOM, null, null));
        assertThat(declined).isEqualTo(new NotificationTexts.Inbox("Мама отклонила запрос: 5,00 BYN · На обед", "error"));
        assertThat(NotificationTexts.requestResolved(resolved(RequestKind.PIGGY_DELETE, false, "1", "Удалить копилку «Велосипед»", MOM, "Велосипед", null)).text())
                .isEqualTo("Мама отклонила запрос: удалить копилку «велосипед»");
    }

    @Test
    void otherTexts() {
        assertThat(NotificationTexts.taskSubmitted(new TaskSubmitted(UUID.randomUUID(), Instant.now(), "f", "misha", "Миша", "t1",
                "Прочитать 20 страниц книги", Money.of("2"))).text()).isEqualTo("Прочитать 20 страниц книги · награда 2,00 BYN");
        assertThat(NotificationTexts.taskApproved(new TaskApproved(UUID.randomUUID(), Instant.now(), "f", "misha", "t1", "Убрать",
                Money.of("1.5"), 5)).text()).isEqualTo("Задание «Убрать» принято: +1,50 BYN и 5 баллов");
        var piggy = new PiggyCompleted(UUID.randomUUID(), Instant.now(), "f", "misha", "Миша", "p", "Конструктор", Money.of("8"));
        assertThat(NotificationTexts.piggyCompletedInbox(piggy).text()).isEqualTo("Копилка «Конструктор» собрана! 8,00 BYN зачислены на счёт");
        assertThat(NotificationTexts.piggyCompletedFeed(piggy).title()).isEqualTo("Миша: копилка «Конструктор» собрана");
        var frozen = new AccountFrozen(UUID.randomUUID(), Instant.now(), "f", "misha", "Миша", "Карта учащегося", "•• 7890", MOM);
        assertThat(NotificationTexts.frozen(frozen)).isEqualTo(new NotificationTexts.Feed("alert", "Счёт Миша заблокирован", "Карта учащегося •• 7890"));
        assertThat(NotificationTexts.frozenInbox(frozen)).isEqualTo(new NotificationTexts.Inbox("Мама заблокировала счёт", "error"));
        assertThat(NotificationTexts.paymentDeclined(new PaymentDeclined(UUID.randomUUID(), Instant.now(), "f", "misha", "Миша", "o",
                "Зал игровых автоматов", Money.of("5"), "7995", ErrorCode.HARD_BLOCK, "«Азартные игры и ставки» — запрещено для несовершеннолетних")).title())
                .isEqualTo("Отклонена оплата: Зал игровых автоматов");
        assertThat(NotificationTexts.paymentCompleted(new PaymentCompleted(UUID.randomUUID(), Instant.now(), "f", "misha", "Миша", false, "o",
                "Буфет школы №12", Money.of("2.40"), "5462", "cafe", "Кафе и столовые", null)))
                .isEqualTo(new NotificationTexts.Feed("info", "Миша оплатил «Буфет школы №12»", "2,40 BYN · Кафе и столовые"));
    }
}
