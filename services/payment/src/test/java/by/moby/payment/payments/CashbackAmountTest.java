package by.moby.payment.payments;

import by.moby.common.money.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CashbackAmountTest {

    @Test
    @DisplayName("Кэшбэк: процент от покупки с округлением HALF_UP, не меньше 1 копейки")
    void percentOfPurchaseRoundedHalfUp() {
        assertThat(QrPaymentService.cashbackAmount(Money.of("2.40"), 5)).isEqualTo(Money.of("0.12"));
        assertThat(QrPaymentService.cashbackAmount(Money.of("0.30"), 5)).isEqualTo(Money.of("0.02"));
        assertThat(QrPaymentService.cashbackAmount(Money.of("8.60"), 3)).isEqualTo(Money.of("0.26"));
        assertThat(QrPaymentService.cashbackAmount(Money.of("0.10"), 3)).isEqualTo(Money.of("0.01"));
    }
}
