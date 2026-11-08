package by.moby.payment.limits;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Optional;

/** Единица срока лимита (LIMIT_UNITS в rules.js). {@code days} — только для сравнения «короче/длиннее». */
public enum LimitUnit {
    DAY("day", 1, "день", "дня", "дней"),
    WEEK("week", 7, "неделя", "недели", "недель"),
    MONTH("month", 30, "месяц", "месяца", "месяцев");

    private final String key;
    private final int days;
    private final String[] forms;

    LimitUnit(String key, int days, String... forms) {
        this.key = key;
        this.days = days;
        this.forms = forms;
    }

    @JsonValue
    public String key() {
        return key;
    }

    public int days() {
        return days;
    }

    String form(int n) {
        int m10 = n % 10;
        int m100 = n % 100;
        if (m10 == 1 && m100 != 11) {
            return forms[0];
        }
        if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) {
            return forms[1];
        }
        return forms[2];
    }

    public static Optional<LimitUnit> fromKey(String key) {
        return Arrays.stream(values()).filter(u -> u.key.equals(key)).findFirst();
    }
}
