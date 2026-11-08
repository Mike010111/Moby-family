package by.moby.rewards.artifacts;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Каталог артефактов и магазин недели (ARTIFACTS / SHOP в artifacts.js). */
public final class ArtifactCatalog {

    /** {@code category} — на какие покупки действует кэшбэк; у разных возрастов может отличаться. */
    public record Artifact(String id, Slot slot, String name, Rarity rarity, Map<Cohort, String> category) {

        public String categoryFor(Cohort cohort) {
            return category.get(cohort);
        }
    }

    public static final List<Artifact> ALL = List.of(
            a("nut", Slot.HANDS, "Лесной орех", Rarity.COMMON, "food", "food"),
            a("coin", Slot.HANDS, "Монета 2 рубля", Rarity.RARE, "books", "transport"),
            a("gamepad", Slot.HANDS, "Геймпад", Rarity.RARE, "entertainment", "subscriptions"),
            a("diamond", Slot.HANDS, "Бриллиант", Rarity.LEGENDARY, "cafe", "cafe"),
            a("glasses-blue", Slot.GLASSES, "Синие очки", Rarity.RARE, "transport", "transport"),
            a("glasses-dark", Slot.GLASSES, "Тёмные очки", Rarity.COMMON, "sport", "sport"),
            a("vest-brown", Slot.OUTER, "Коричневый жилет", Rarity.RARE, "clothes", "clothes"));

    /** Что продаётся на неделе слота (в демо заготовлена только неделя «в руках»). */
    public static final Map<Slot, List<String>> SHOP = Map.of(Slot.HANDS, List.of("nut", "coin", "gamepad", "diamond"));

    private ArtifactCatalog() {
    }

    private static Artifact a(String id, Slot slot, String name, Rarity rarity, String junior, String teen) {
        return new Artifact(id, slot, name, rarity, Map.of(Cohort.JUNIOR, junior, Cohort.TEEN, teen));
    }

    public static Optional<Artifact> byId(String id) {
        return ALL.stream().filter(a -> a.id().equals(id)).findFirst();
    }

    public static List<String> shopFor(Slot slot) {
        return SHOP.getOrDefault(slot, List.of());
    }
}
