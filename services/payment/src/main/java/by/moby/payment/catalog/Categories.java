package by.moby.payment.catalog;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/** Справочник категорий и MCC — порт CATEGORIES, HARD_BLOCKS, groupOf, groupLabel, categoriesFor. */
public final class Categories {

    public static final String P2P_MCC = "4829";
    public static final String ONLINE = "online";
    public static final String P2P = "p2p";

    /** Служебные «MCC» операций, которые не являются тратами. */
    public static final String TOPUP = "topup";
    public static final String PIGGY = "piggy";
    public static final String CASHBACK = "cashback";
    private static final Set<String> SERVICE = Set.of(TOPUP, PIGGY, CASHBACK);
    private static final Map<String, String> SERVICE_LABELS = Map.of(PIGGY, "Копилка", TOPUP, "Пополнение", CASHBACK, "Кэшбэк");

    public static final List<Category> ALL = List.of(
            c("food", "Продукты и супермаркеты", "cart", "5411"),
            c("cafe", "Кафе и столовые", "coffee", "5812", "5462"),
            c("fastfood", "Фастфуд", "burger", "5814"),
            c("transport", "Транспорт и такси", "bus", "4111", "4121"),
            c("clothes", "Одежда и обувь", "shirt", "5651"),
            c("electronics", "Электроника", "phone", "5732"),
            c("books", "Книги и канцтовары", "book", "5942", "5943"),
            c("education", "Образование и кружки", "grad", "8299"),
            c("sport", "Спорт", "dumbbell", "5941"),
            c("pharmacy", "Аптеки", "pill", "5912"),
            c("games", "Игры и донаты", "gamepad", "5816"),
            c("entertainment", "Кино и развлечения", "film", "7832"),
            c("subscriptions", "Подписки и сервисы", "music", "5735", "5815"),
            new Category(ONLINE, "Покупки в интернете", "globe", List.of(), true, null),
            c("cash", "Снятие наличных", "banknote", "6011"),
            new Category(P2P, "Переводы", "send", List.of(P2P_MCC), false, Cohort.TEEN));

    /** Банковские хард-блоки — родитель не может их включить. */
    public static final List<Category> HARD_BLOCKS = List.of(
            c("gambling", "Азартные игры и ставки", "dice", "7995"),
            c("adult", "Категория 18+", "lock", "5967"));

    private static final Map<String, Category> BY_KEY = new LinkedHashMap<>();
    private static final Map<String, Category> BY_MCC = new HashMap<>();

    static {
        Stream.concat(ALL.stream(), HARD_BLOCKS.stream()).forEach(cat -> {
            BY_KEY.put(cat.key(), cat);
            cat.mcc().forEach(m -> BY_MCC.put(m, cat));
        });
    }

    private Categories() {
    }

    private static Category c(String key, String label, String icon, String... mcc) {
        return new Category(key, label, icon, List.of(mcc), false, null);
    }

    public static Optional<Category> byKey(String key) {
        return Optional.ofNullable(BY_KEY.get(key));
    }

    /** Группа (ключ категории) по MCC. Служебные операции — {@code topup} / {@code piggy} / {@code cashback}. */
    public static String groupOf(String mcc) {
        if (SERVICE.contains(mcc)) {
            return mcc;
        }
        Category cat = BY_MCC.get(mcc);
        return cat == null ? "other" : cat.key();
    }

    public static boolean isService(String group) {
        return SERVICE.contains(group);
    }

    public static String groupLabel(String mcc) {
        Category cat = BY_MCC.get(mcc);
        if (cat != null) {
            return cat.label();
        }
        return SERVICE_LABELS.getOrDefault(mcc, "Прочее");
    }

    public static String label(String key) {
        return byKey(key).map(Category::label).orElse(key);
    }

    public static Optional<Category> hardBlock(String group) {
        return HARD_BLOCKS.stream().filter(h -> h.key().equals(group)).findFirst();
    }

    /** Категории, применимые к возрасту ребёнка. */
    public static List<Category> categoriesFor(Cohort cohort) {
        return ALL.stream().filter(c -> c.minCohort() == null || c.minCohort() == cohort).toList();
    }

    public static boolean availableFor(Cohort cohort, String key) {
        return categoriesFor(cohort).stream().anyMatch(c -> c.key().equals(key));
    }
}
