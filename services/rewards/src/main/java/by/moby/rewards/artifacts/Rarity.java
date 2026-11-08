package by.moby.rewards.artifacts;

import com.fasterxml.jackson.annotation.JsonValue;

/** Редкость → базовый кэшбэк и цена в баллах. Продление стоит по редкости нового кэшбэка. */
public enum Rarity {
    COMMON("common", "Обычный", 3, 50),
    RARE("rare", "Редкий", 4, 100),
    LEGENDARY("legendary", "Легендарный", 5, 200);

    public static final int MIN_CASHBACK = 3;

    private final String key;
    private final String label;
    private final int cashback;
    private final int price;

    Rarity(String key, String label, int cashback, int price) {
        this.key = key;
        this.label = label;
        this.cashback = cashback;
        this.price = price;
    }

    public static Rarity byCashback(int percent) {
        return percent >= 5 ? LEGENDARY : percent >= 4 ? RARE : COMMON;
    }

    @JsonValue
    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public int cashback() {
        return cashback;
    }

    public int price() {
        return price;
    }
}
