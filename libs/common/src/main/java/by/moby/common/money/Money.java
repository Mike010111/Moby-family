package by.moby.common.money;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Сумма в BYN: всегда scale 2, без double. Неизменяемый value object.
 * Отрицательные значения допустимы (знаковые проводки и строки выписки).
 */
public record Money(BigDecimal amount) implements Comparable<Money> {

    public static final String CURRENCY = "BYN";
    public static final Money ZERO = new Money(BigDecimal.ZERO);

    /** Как {@code parseMoney} в rules.js: положительное число, не больше двух знаков, допускается «5,25». */
    private static final Pattern INPUT = Pattern.compile("^\\d+(\\.\\d{1,2})?$");

    public Money {
        Objects.requireNonNull(amount, "amount");
        try {
            amount = amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Money supports at most 2 fraction digits: " + amount, e);
        }
    }

    @JsonCreator
    public static Money of(BigDecimal amount) {
        return new Money(amount);
    }

    public static Money of(String amount) {
        return new Money(new BigDecimal(amount));
    }

    /** Округление до копеек «по-школьному» (HALF_UP), как {@code round2} в rules.js для неотрицательных сумм. */
    public static Money rounded(BigDecimal amount) {
        return new Money(amount.setScale(2, RoundingMode.HALF_UP));
    }

    /** Разбор пользовательского ввода. Пусто — если ввод не соответствует формату. */
    public static Optional<Money> parse(String raw) {
        String s = raw == null ? "" : raw.trim().replaceFirst(",", ".");
        if (!INPUT.matcher(s).matches()) {
            return Optional.empty();
        }
        return Optional.of(new Money(new BigDecimal(s)));
    }

    @JsonValue
    public BigDecimal amount() {
        return amount;
    }

    public Money plus(Money other) {
        return new Money(amount.add(other.amount));
    }

    public Money minus(Money other) {
        return new Money(amount.subtract(other.amount));
    }

    public Money negate() {
        return new Money(amount.negate());
    }

    public Money abs() {
        return new Money(amount.abs());
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isGreaterThan(Money other) {
        return compareTo(other) > 0;
    }

    public boolean isLessThan(Money other) {
        return compareTo(other) < 0;
    }

    public static Money max(Money a, Money b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    public static Money min(Money a, Money b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    @Override
    public int compareTo(Money other) {
        return amount.compareTo(other.amount);
    }

    /** «1 200,50 BYN» — как {@code money()} в format.js (ru-RU, неразрывный пробел между разрядами). */
    public String format() {
        return formatBare() + " " + CURRENCY;
    }

    /** «1 200,50» — без валюты. */
    public String formatBare() {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setDecimalSeparator(',');
        symbols.setGroupingSeparator(' ');
        symbols.setMinusSign('-');
        DecimalFormat format = new DecimalFormat("#,##0.00", symbols);
        return format.format(amount);
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + CURRENCY;
    }
}
