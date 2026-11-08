package by.moby.payment.scenarios;

import by.moby.common.error.BusinessException;
import by.moby.common.money.Money;
import by.moby.payment.account.AccountOperationRepository;
import by.moby.payment.limits.SpendingLimitRepository;
import by.moby.payment.payments.QrPaymentResult;
import by.moby.payment.payments.QrPaymentService;
import by.moby.payment.support.PaymentIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Требование №3: проверка лимита и списание — в одной транзакции под {@code SELECT … FOR UPDATE}.
 * 50 параллельных оплат не пробивают ни лимит, ни баланс.
 */
class ConcurrentPaymentsTest extends PaymentIntegrationTest {

    private static final int THREADS = 50;
    private static final Money BUFFET = Money.of("2.40");

    @Autowired
    QrPaymentService payments;
    @Autowired
    SpendingLimitRepository limits;
    @Autowired
    AccountOperationRepository operations;

    @Test
    @DisplayName("50 параллельных оплат не пробивают дневной лимит")
    void limitHolds() throws Exception {
        // Лимит «Сегодня · все» 15 BYN, сегодня уже потрачено 3,60 → остаток 11,40 → проходят ровно 4 оплаты по 2,40
        List<QrPaymentResult> results = payInParallel();
        long completed = results.stream().filter(r -> r.status() == QrPaymentResult.Status.COMPLETED).count();
        assertThat(completed).isEqualTo(4);
        assertThat(results).filteredOn(r -> r.status() == QrPaymentResult.Status.DECLINED)
                .allSatisfy(r -> assertThat(r.code().name()).isEqualTo("LIMIT"));
        assertThat(balance("misha")).isEqualTo(Money.of("12.40").minus(BUFFET.plus(BUFFET).plus(BUFFET).plus(BUFFET)));
        assertLedgerReconciles();
    }

    @Test
    @DisplayName("50 параллельных оплат не уводят баланс в минус")
    void balanceHolds() throws Exception {
        limits.deleteAll();
        // Без лимитов ограничивает только баланс 12,40 → проходят 5 оплат по 2,40, остаётся 0,40
        List<QrPaymentResult> results = payInParallel();
        assertThat(results).filteredOn(r -> r.status() == QrPaymentResult.Status.COMPLETED).hasSize(5);
        assertThat(results).filteredOn(r -> r.status() == QrPaymentResult.Status.DECLINED)
                .allSatisfy(r -> assertThat(r.code().name()).isEqualTo("FUNDS"));
        assertThat(balance("misha")).isEqualTo(Money.of("0.40"));
        assertLedgerReconciles();
    }

    private List<QrPaymentResult> payInParallel() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<QrPaymentResult>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> {
                start.await();
                return payments.pay("misha", "m-buffet", UUID.randomUUID().toString());
            });
        }
        try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
            List<Future<QrPaymentResult>> futures = tasks.stream().map(pool::submit).toList();
            start.countDown();
            List<QrPaymentResult> results = new ArrayList<>();
            for (Future<QrPaymentResult> f : futures) {
                results.add(f.get());
            }
            return results;
        }
    }
}
