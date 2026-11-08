package by.moby.rewards.artifacts;

import by.moby.common.money.Money;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Магазин и маскот ребёнка: всё, что нужно экрану (child.artifacts + points в прототипе). */
public record ChildRewardsView(
        String childId,
        String cohort,
        int points,
        String weekSlot,
        Instant weekStart,
        Instant weekEnd,
        List<String> cycle,
        Map<String, Owned> owned,
        List<String> soldOut,
        List<ShopItem> shop,
        Renew renew) {

    public record Owned(String id, String slot, String name, String rarity, String category, int cashback, int cycles,
                        Instant boughtAt, Instant activeUntil, boolean active, Money cashbackEarned) {
    }

    public record ShopItem(String id, String name, String slot, String rarity, int cashback, String category, int price,
                           String status, int lack, String replaces) {
    }

    public record Renew(String artifactId, String name, int cashback, String rarity, int price) {
    }
}
