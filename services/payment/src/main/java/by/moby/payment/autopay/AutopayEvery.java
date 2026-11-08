package by.moby.payment.autopay;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Optional;

public enum AutopayEvery {
    DAY("day"), WEEK("week"), MONTH("month");

    private final String key;

    AutopayEvery(String key) {
        this.key = key;
    }

    @JsonValue
    public String key() {
        return key;
    }

    public static Optional<AutopayEvery> fromKey(String key) {
        return Arrays.stream(values()).filter(e -> e.key.equals(key)).findFirst();
    }
}
