package by.moby.family.overview;

import by.moby.common.money.Money;

import java.time.Instant;
import java.util.List;

/** Семья глазами текущего пользователя: родитель видит всех детей, ребёнок — только себя. */
public record FamilyView(String familyId, Me me, List<Relative> relatives, List<Child> children) {

    public record Me(String memberId, String role) {
    }

    public record Relative(String id, String name, String label, String genitive, String dative, String accusative,
                           boolean main, boolean female) {
    }

    public record Child(String id, String name, String nameGen, String surname, int age, String cohort, boolean frozen,
                        GeoView geo, int battery, List<TaskItem> tasks, List<PiggyItem> piggies, LessonsView lessons) {
    }

    public record GeoView(String place, double x, double y, double distance, Instant updated) {
    }

    public record TaskItem(String id, String title, Money reward, String status) {
    }

    public record PiggyItem(String id, String goal, Money target, Money saved) {
    }

    public record LessonsView(int done, int total) {
    }
}
