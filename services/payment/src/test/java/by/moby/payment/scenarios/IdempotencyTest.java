package by.moby.payment.scenarios;

import by.moby.common.money.Money;
import by.moby.payment.payments.QrPaymentResult;
import by.moby.payment.payments.QrPaymentService;
import by.moby.payment.support.PaymentIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static by.moby.payment.support.TestUsers.anna;
import static by.moby.payment.support.TestUsers.misha;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Требование №4: повтор с тем же Idempotency-Key возвращает тот же ответ и ничего не списывает повторно. */
class IdempotencyTest extends PaymentIntegrationTest {

    @Autowired
    QrPaymentService payments;

    @Test
    @DisplayName("Повтор оплаты с тем же ключом — тот же ответ, одно списание")
    void repeatedPaymentIsReplayed() throws Exception {
        String first = payWithKey("k-1", "m-buffet").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String second = payWithKey("k-1", "m-buffet").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat(second).isEqualTo(first);
        assertThat(balance("misha")).isEqualTo(Money.of("10.00"));
        assertLedgerReconciles();
    }

    @Test
    @DisplayName("Повтор отклонённой оплаты возвращает тот же отказ и не пишет вторую строку выписки")
    void repeatedDeclineIsReplayed() throws Exception {
        payWithKey("k-2", "m-slots").andExpect(jsonPath("$.code").value("HARD_BLOCK"));
        String operationId = payWithKey("k-2", "m-slots").andExpect(jsonPath("$.code").value("HARD_BLOCK"))
                .andReturn().getResponse().getContentAsString();
        getAs(misha(), "/api/payment/children/misha/operations")
                .andExpect(jsonPath("$[0].status").value("DECLINED"))
                .andExpect(jsonPath("$[1].status").value("COMPLETED"));
        assertThat(operationId).contains("operationId");
    }

    @Test
    @DisplayName("Тот же ключ для другого запроса — 409 IDEMPOTENCY_MISMATCH; без ключа — 400")
    void keyMisuse() throws Exception {
        payWithKey("k-3", "m-buffet").andExpect(status().isCreated());
        payWithKey("k-3", "m-office").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_MISMATCH"));
        mvc.perform(post("/api/payment/children/misha/qr-payments").with(misha())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"merchantId\":\"m-buffet\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY"));
    }

    @Test
    @DisplayName("Одновременные повторы одного пополнения — одно списание")
    void concurrentRetriesOfOneTopUp() {
        List<CompletableFuture<Integer>> calls = IntStream.range(0, 10)
                .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return mvc.perform(post("/api/payment/children/misha/topups").with(anna())
                                        .header("Idempotency-Key", "topup-once")
                                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"5\"}"))
                                .andReturn().getResponse().getStatus();
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }))
                .toList();
        assertThat(calls.stream().map(CompletableFuture::join)).containsOnly(201);
        assertThat(balance("misha")).isEqualTo(Money.of("17.40"));
        assertLedgerReconciles();
    }

    @Test
    @DisplayName("Сервисный уровень: повтор возвращает сохранённый результат")
    void serviceLevelReplay() {
        QrPaymentResult a = payments.pay("misha", "m-office", "svc-1");
        QrPaymentResult b = payments.pay("misha", "m-office", "svc-1");
        assertThat(b).isEqualTo(a);
    }

    private org.springframework.test.web.servlet.ResultActions payWithKey(String key, String merchant) throws Exception {
        return mvc.perform(post("/api/payment/children/misha/qr-payments").with(misha())
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content("{\"merchantId\":\"" + merchant + "\"}"));
    }
}
