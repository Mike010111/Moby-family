package by.moby.common.money;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void parsesLikeTheJsPrototype() {
        assertThat(Money.parse("10,55")).contains(Money.of("10.55"));
        assertThat(Money.parse(" 5 ")).contains(Money.of("5"));
        assertThat(Money.parse("10.555")).isEmpty();
        assertThat(Money.parse("-3")).isEmpty();
        assertThat(Money.parse("")).isEmpty();
        assertThat(Money.parse(null)).isEmpty();
        assertThat(Money.parse("1,2,3")).isEmpty();
    }

    @Test
    void alwaysHasScaleTwo() {
        assertThat(Money.of("5").amount()).isEqualByComparingTo("5.00").hasScaleOf(2);
        assertThat(Money.of("5").plus(Money.of("0.1"))).isEqualTo(Money.of("5.10"));
        assertThatThrownBy(() -> Money.of(new BigDecimal("1.234"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(Money.rounded(new BigDecimal("0.125"))).isEqualTo(Money.of("0.13"));
    }

    @Test
    void formatsAsRussianLocale() {
        assertThat(Money.of("35").format()).isEqualTo("35,00 BYN");
        assertThat(Money.of("1200.5").format()).isEqualTo("1 200,50 BYN");
    }

    @Test
    void comparesByValue() {
        assertThat(Money.of("2.40").isGreaterThan(Money.of("2.39"))).isTrue();
        assertThat(Money.max(Money.ZERO, Money.of("-1"))).isEqualTo(Money.ZERO);
        assertThat(Money.of("1").negate().isNegative()).isTrue();
    }
}
