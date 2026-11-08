package by.moby.rewards.artifacts;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.List;

/** Слоты маскота. У 7–13 четыре слота (цикл 4 недели), у 14–17 добавляется обувь (5 недель). */
public enum Slot {
    HAT("hat", "Головной убор"),
    GLASSES("glasses", "Очки"),
    OUTER("outer", "Верхняя одежда"),
    HANDS("hands", "В руках"),
    SHOES("shoes", "Обувь");

    private final String key;
    private final String label;

    Slot(String key, String label) {
        this.key = key;
        this.label = label;
    }

    @JsonValue
    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    /** Порядок недель в цикле; длина цикла в неделях = число слотов. */
    public static List<Slot> cycle(Cohort cohort) {
        return cohort == Cohort.JUNIOR ? List.of(HAT, GLASSES, OUTER, HANDS) : List.of(HAT, GLASSES, OUTER, HANDS, SHOES);
    }

    public static Slot fromKey(String key) {
        return Arrays.stream(values()).filter(s -> s.key.equals(key)).findFirst().orElseThrow();
    }
}
