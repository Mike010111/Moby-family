package by.moby.payment.scenarios;

import by.moby.common.money.Money;
import by.moby.payment.payments.Perks;
import by.moby.payment.support.PaymentIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static by.moby.payment.support.TestUsers.anna;
import static by.moby.payment.support.TestUsers.ivan;
import static by.moby.payment.support.TestUsers.misha;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** JUnit-аналоги сценариев tests/store.test.js, относящихся к деньгам, лимитам и контролю. */
class PaymentScenariosTest extends PaymentIntegrationTest {

    @Test
    @DisplayName("Суммы — не больше двух знаков после запятой")
    void amountsHaveAtMostTwoDecimals() throws Exception {
        postAs(anna(), "/api/payment/children/misha/topups", "{\"amount\":\"1.234\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AMOUNT"));
        Money p0 = parentBalance();
        postAs(anna(), "/api/payment/children/misha/topups", "{\"amount\":\"1,23\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.childBalance").value(13.63));
        assertThat(parentBalance()).isEqualTo(p0.minus(Money.of("1.23")));
        getAs(misha(), "/api/payment/children/misha/operations")
                .andExpect(jsonPath("$[0].title").value("Пополнение от мамы"))
                .andExpect(jsonPath("$[0].mcc").value("topup"));
        assertLedgerReconciles();
    }

    @Test
    @DisplayName("Лимит меньше уже потраченного: ничего не ломается, оплата отклоняется")
    void limitBelowSpent() throws Exception {
        putAs(anna(), "/api/payment/children/misha/limits/l-m1", """
                {"count":1,"unit":"day","amount":"1","categories":[]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usage.remaining").value(0))
                .andExpect(jsonPath("$.usage.exhausted").value(true))
                .andExpect(jsonPath("$.usage.percent").value(100))
                .andExpect(jsonPath("$.usage.overBy").value(2.6));
        pay(misha(), "misha", "m-buffet")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("LIMIT"))
                .andExpect(jsonPath("$.detail").value("Превышен лимит «Сегодня · Все категории»: осталось 0,00 BYN"))
                .andExpect(jsonPath("$.merchant.id").value("m-buffet"));
        getAs(misha(), "/api/payment/children/misha/operations")
                .andExpect(jsonPath("$[0].status").value("DECLINED"))
                .andExpect(jsonPath("$[0].declineCode").value("LIMIT"));
    }

    @Test
    @DisplayName("Два лимита с одинаковым непустым набором категорий создать нельзя, с пустым — можно")
    void duplicateLimits() throws Exception {
        String uri = "/api/payment/children/misha/limits";
        postAs(anna(), uri, limit(2, "week", "30", "\"cafe\"")).andExpect(jsonPath("$.code").value("DUPLICATE")).andExpect(status().isConflict());
        postAs(anna(), uri, limit(2, "week", "30", "\"books\",\"cafe\"")).andExpect(status().isCreated());
        postAs(anna(), uri, limit(2, "week", "30", "\"cafe\",\"books\"")).andExpect(jsonPath("$.code").value("DUPLICATE"));
        postAs(anna(), uri, limit(2, "week", "30", "")).andExpect(status().isCreated());
        postAs(anna(), uri, limit(51, "week", "30", "")).andExpect(jsonPath("$.code").value("COUNT"));
        postAs(anna(), uri, limit(2, "week", "30", "\"p2p\"")).andExpect(jsonPath("$.code").value("CATEGORY"));
        postAs(anna(), uri, limit(2, "year", "30", "")).andExpect(jsonPath("$.code").value("UNIT"));
    }

    @Test
    @DisplayName("Иерархия: лимит на более длинный срок не меньше более короткого, если покрывает его категории")
    void limitHierarchy() throws Exception {
        String uri = "/api/payment/children/misha/limits";
        postAs(anna(), uri, limit(1, "week", "10", ""))
                .andExpect(jsonPath("$.code").value("HIERARCHY"))
                .andExpect(jsonPath("$.detail").value(containsString("не меньше 15,00 BYN — это лимит «Сегодня · Все категории»")));
        postAs(anna(), uri, limit(1, "week", "15", "")).andExpect(status().isCreated());
        postAs(anna(), uri, limit(1, "month", "14", "")).andExpect(jsonPath("$.code").value("HIERARCHY"));
        postAs(anna(), uri, limit(3, "day", "25", "\"books\"")).andExpect(jsonPath("$.code").value("HIERARCHY"));
        postAs(anna(), uri, limit(1, "month", "5", "\"books\"")).andExpect(status().isCreated());
        putAs(anna(), uri + "/l-m1", limit(1, "day", "16", ""))
                .andExpect(jsonPath("$.code").value("HIERARCHY"))
                .andExpect(jsonPath("$.detail").value(containsString("на более длинный срок")));
    }

    @Test
    @DisplayName("Хард-блок азартных игр и тумблер категории")
    void hardBlockAndCategoryToggle() throws Exception {
        pay(misha(), "misha", "m-slots").andExpect(jsonPath("$.code").value("HARD_BLOCK"))
                .andExpect(jsonPath("$.detail").value("«Азартные игры и ставки» — запрещено для несовершеннолетних"));
        pay(misha(), "misha", "m-burger").andExpect(jsonPath("$.code").value("PARENT_CATEGORY"))
                .andExpect(jsonPath("$.detail").value("Категория «Фастфуд» запрещена"));
        putAs(anna(), "/api/payment/children/misha/categories/fastfood", "{\"allowed\":true}")
                .andExpect(jsonPath("$.allow.fastfood").value(true));
        pay(misha(), "misha", "m-burger")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.legal").value(containsString("ст. 27 ГК РБ")));
        putAs(anna(), "/api/payment/children/misha/categories/gambling", "{\"allowed\":true}")
                .andExpect(jsonPath("$.code").value("CATEGORY"));
        assertLedgerReconciles();
    }

    @Test
    @DisplayName("Блокировка счёта: денежные операции стоят")
    void frozenAccountStopsMoney() throws Exception {
        putAs(anna(), "/api/payment/children/misha/frozen", "{\"frozen\":true}").andExpect(jsonPath("$.frozen").value(true));
        postAs(anna(), "/api/payment/children/misha/topups", "{\"amount\":\"5\"}")
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("FROZEN"));
        pay(misha(), "misha", "m-buffet").andExpect(jsonPath("$.code").value("FROZEN"));
        pay(misha(), "misha", "m-slots").andExpect(jsonPath("$.code").value("HARD_BLOCK"));
        putAs(anna(), "/api/payment/children/misha/frozen", "{\"frozen\":false}").andExpect(jsonPath("$.frozen").value(false));
        pay(misha(), "misha", "m-buffet").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("P2P сверх лимита: запрос → одобрение → разовый перевод не выше одобренного")
    void p2pOverLimitWithApproval() throws Exception {
        quote("10").andExpect(jsonPath("$.mode").value("SEND"));
        quote("35").andExpect(jsonPath("$.mode").value("REQUEST")).andExpect(jsonPath("$.code").value("NEEDS_APPROVAL"))
                .andExpect(jsonPath("$.reason").value("Сумма выше лимита: доступно 24,50 BYN"));
        send("35").andExpect(jsonPath("$.code").value("NEEDS_APPROVAL")).andExpect(jsonPath("$.mode").value("REQUEST"));

        postAs(anna(), "/api/payment/internal/children/ivan/p2p-approvals", "{\"requestId\":\"r-1\",\"max\":35}")
                .andExpect(status().isCreated());
        quote("40").andExpect(jsonPath("$.mode").value("REQUEST"));
        quote("10").andExpect(jsonPath("$.mode").value("SEND"));
        Money before = balance("ivan");
        send("30").andExpect(status().isCreated()).andExpect(jsonPath("$.mode").value("APPROVED"));
        assertThat(balance("ivan")).isEqualTo(before.minus(Money.of("30")));
        quote("30").andExpect(jsonPath("$.mode").value("REQUEST"));
        getAs(ivan(), "/api/payment/children/ivan/operations")
                .andExpect(jsonPath("$[0].note").value("Сверх лимита · одобрено маме"))
                .andExpect(jsonPath("$[0].title").value("Перевод: Катя Соколова"));
        getAs(ivan(), "/api/payment/children/ivan/account").andExpect(jsonPath("$.approvals[0].status").value("USED"));
        assertLedgerReconciles();
    }

    @Test
    @DisplayName("P2P у младшего запрещён, родитель может выключить переводы подростку")
    void p2pAgeAndParentSwitch() throws Exception {
        putAs(anna(), "/api/payment/children/misha/categories/p2p", "{\"allowed\":true}").andExpect(jsonPath("$.code").value("AGE"));
        getAs(misha(), "/api/payment/children/misha/p2p/quote?amount=1").andExpect(jsonPath("$.code").value("AGE"));
        putAs(anna(), "/api/payment/children/ivan/categories/p2p", "{\"allowed\":false}").andExpect(status().isOk());
        send("1").andExpect(jsonPath("$.code").value("PARENT_P2P"));
    }

    @Test
    @DisplayName("Подписки подростка")
    void teenSubscriptions() throws Exception {
        perform(patch("/api/payment/children/ivan/subscriptions/sub1").contentType("application/json").content("{\"price\":\"7,49\"}"), ivan())
                .andExpect(jsonPath("$.price").value(7.49));
        perform(patch("/api/payment/children/ivan/subscriptions/sub1").contentType("application/json").content("{\"active\":false}"), ivan())
                .andExpect(jsonPath("$.active").value(false));
        perform(delete("/api/payment/children/ivan/subscriptions/sub1"), ivan()).andExpect(status().isNoContent());
        getAs(ivan(), "/api/payment/children/ivan/account").andExpect(jsonPath("$.subscriptions", hasSize(2)));
    }

    @Test
    @DisplayName("Автоперевод настраивается, но не дублируется")
    void autopayUpdate() throws Exception {
        putAs(anna(), "/api/payment/children/misha/autopay", """
                {"enabled":true,"amount":"12,5","every":"month","monthDay":5}""")
                .andExpect(status().isOk());
        getAs(anna(), "/api/payment/children/misha/account")
                .andExpect(jsonPath("$.autopay.enabled").value(true))
                .andExpect(jsonPath("$.autopay.amount").value(12.5))
                .andExpect(jsonPath("$.autopay.every").value("month"))
                .andExpect(jsonPath("$.autopay.weekday").value(0))
                .andExpect(jsonPath("$.autopay.monthDay").value(5));
        putAs(anna(), "/api/payment/children/misha/autopay", "{\"enabled\":true,\"amount\":\"5\",\"every\":\"month\",\"monthDay\":29}")
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    @Test
    @DisplayName("Кэшбэк начисляется при оплате по QR в категории артефакта")
    void cashbackOnQrPayment() throws Exception {
        when(rewards.perks(eq("misha"), eq("cafe"), any())).thenReturn(new Perks(2, new Perks.Cashback(5, "diamond", "Бриллиант")));
        Money b0 = balance("misha");
        pay(misha(), "misha", "m-buffet")
                .andExpect(jsonPath("$.cashback.pct").value(5))
                .andExpect(jsonPath("$.cashback.value").value(0.12))
                .andExpect(jsonPath("$.earned").value(2));
        assertThat(balance("misha")).isEqualTo(b0.minus(Money.of("2.40")).plus(Money.of("0.12")));
        getAs(misha(), "/api/payment/children/misha/operations")
                .andExpect(jsonPath("$[0].mcc").value("cashback"))
                .andExpect(jsonPath("$[0].title").value("Кэшбэк 5% · Бриллиант"))
                .andExpect(jsonPath("$[0].note").value("Кафе и столовые"));
        assertLedgerReconciles();
    }

    @Test
    @DisplayName("Оплата проходит без кэшбэка, если rewards недоступен")
    void paymentWithoutRewards() throws Exception {
        when(rewards.perks(any(), any(), any())).thenReturn(Perks.unknown());
        pay(misha(), "misha", "m-buffet").andExpect(status().isCreated()).andExpect(jsonPath("$.cashback").doesNotExist());
    }

    private static String limit(int count, String unit, String amount, String categories) {
        return "{\"count\":%d,\"unit\":\"%s\",\"amount\":\"%s\",\"categories\":[%s]}".formatted(count, unit, amount, categories);
    }

    private org.springframework.test.web.servlet.ResultActions quote(String amount) throws Exception {
        return getAs(ivan(), "/api/payment/children/ivan/p2p/quote?amount=" + amount);
    }

    private org.springframework.test.web.servlet.ResultActions send(String amount) throws Exception {
        return postAs(ivan(), "/api/payment/children/ivan/p2p-transfers", "{\"contactId\":\"c-katya\",\"amount\":\"" + amount + "\"}");
    }
}
