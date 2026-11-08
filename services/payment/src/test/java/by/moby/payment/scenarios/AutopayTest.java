package by.moby.payment.scenarios;

import by.moby.common.money.Money;
import by.moby.payment.autopay.AutopayService;
import by.moby.payment.support.PaymentIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;

import static by.moby.payment.support.TestUsers.anna;
import static by.moby.payment.support.TestUsers.misha;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class AutopayTest extends PaymentIntegrationTest {

    @Autowired
    AutopayService autopay;

    @Test
    @DisplayName("Автоперевод исполняется по расписанию один раз в день")
    void runsOncePerDay() throws Exception {
        // Демо: Миша — по понедельникам (weekday 0), Иван — по пятницам (4); сейчас среда, сегодня уже отмечено
        assertThat(autopay.runDue()).isZero();
        clock.advance(Duration.ofDays(2)); // пятница
        Money ivan0 = balance("ivan");
        assertThat(autopay.runDue()).isEqualTo(1);
        assertThat(autopay.runDue()).isZero();
        assertThat(balance("ivan")).isEqualTo(ivan0.plus(Money.of("40")));

        putAs(anna(), "/api/payment/children/misha/autopay", "{\"enabled\":true,\"amount\":\"3\",\"every\":\"day\"}");
        clock.advance(Duration.ofDays(1));
        Money misha0 = balance("misha");
        Money parent0 = parentBalance();
        assertThat(autopay.runDue()).isEqualTo(1);
        assertThat(balance("misha")).isEqualTo(misha0.plus(Money.of("3")));
        assertThat(parentBalance()).isEqualTo(parent0.minus(Money.of("3")));
        getAs(misha(), "/api/payment/children/misha/operations").andExpect(jsonPath("$[0].title").value("Автоперевод от мамы"));
        assertLedgerReconciles();
    }

    @Test
    @DisplayName("Автоперевод на замороженный счёт пропускается")
    void skipsFrozen() throws Exception {
        putAs(anna(), "/api/payment/children/misha/autopay", "{\"enabled\":true,\"amount\":\"3\",\"every\":\"day\"}");
        putAs(anna(), "/api/payment/children/misha/frozen", "{\"frozen\":true}");
        clock.advance(Duration.ofDays(1));
        Money misha0 = balance("misha");
        autopay.runDue();
        assertThat(balance("misha")).isEqualTo(misha0);
    }
}
