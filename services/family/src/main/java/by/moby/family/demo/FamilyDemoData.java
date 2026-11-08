package by.moby.family.demo;

import by.moby.common.money.Money;
import by.moby.family.lessons.LessonProgress;
import by.moby.family.lessons.LessonProgressRepository;
import by.moby.family.members.Cohort;
import by.moby.family.members.Geo;
import by.moby.family.members.Member;
import by.moby.family.members.MemberRepository;
import by.moby.family.piggies.Piggy;
import by.moby.family.piggies.PiggyRepository;
import by.moby.family.tasks.Task;
import by.moby.family.tasks.TaskRepository;
import by.moby.family.tasks.TaskStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Семья из initial-state.js: Анна (главный родственник), папа, бабушка, Миша (9) и Иван (17). */
@Component
@Profile("demo")
public class FamilyDemoData {

    public static final String FAMILY = "fam-demo";

    private final MemberRepository members;
    private final TaskRepository tasks;
    private final PiggyRepository piggies;
    private final LessonProgressRepository lessons;
    private final JdbcClient jdbc;
    private final Clock clock;

    public FamilyDemoData(MemberRepository members, TaskRepository tasks, PiggyRepository piggies, LessonProgressRepository lessons,
                          JdbcClient jdbc, Clock clock) {
        this.members = members;
        this.tasks = tasks;
        this.piggies = piggies;
        this.lessons = lessons;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public void seedIfEmpty() {
        if (members.count() == 0) {
            seed();
        }
    }

    @Transactional
    public void reset() {
        jdbc.sql("truncate requests, tasks, piggies, lesson_progress, members, processed_events").update();
        seed();
    }

    private void seed() {
        Instant now = Instant.now(clock);
        members.saveAll(List.of(
                Member.relative("mom", FAMILY, "Анна", "Мама", "мамы", "маме", "маму", true, true, 1),
                Member.relative("dad", FAMILY, "Сергей", "Папа", "папы", "папе", "папу", false, false, 2),
                Member.relative("grandma", FAMILY, "Галина", "Бабушка", "бабушки", "бабушке", "бабушку", false, true, 3),
                Member.child("misha", FAMILY, "Миша", "Миши", "Ромейко", 9, Cohort.JUNIOR, false,
                        new Geo("Школа №12", 0.68, 0.32, 1.2, now.minus(Duration.ofMinutes(2))), 64, 4),
                Member.child("ivan", FAMILY, "Иван", "Ивана", "Иванов", 17, Cohort.TEEN, false,
                        new Geo("Библиотека", 0.34, 0.72, 1.6, now.minus(Duration.ofMinutes(6))), 38, 5)));
        tasks.saveAll(List.of(
                task("t1", "misha", "Прочитать 20 страниц книги", "2", TaskStatus.OPEN, now, 3),
                task("t2", "misha", "Получить 10 по математике", "3", TaskStatus.OPEN, now, 2),
                task("t3", "misha", "Убрать в комнате", "1.50", TaskStatus.DONE, now, 1),
                task("ta1", "ivan", "Сдать пробный ЦТ по математике", "10", TaskStatus.OPEN, now, 2),
                task("ta2", "ivan", "Сходить в магазин за продуктами", "3", TaskStatus.DONE, now, 1)));
        piggies.saveAll(List.of(
                new Piggy("pg-bike", FAMILY, "misha", "Велосипед", Money.of("250"), Money.of("86.50"), now.minus(Duration.ofDays(30))),
                new Piggy("pg-laptop", FAMILY, "ivan", "Ноутбук", Money.of("1200"), Money.of("310"), now.minus(Duration.ofDays(60))),
                new Piggy("pg-concert", FAMILY, "ivan", "Билет на концерт", Money.of("60"), Money.of("52"), now.minus(Duration.ofDays(20)))));
        lessons.saveAll(List.of(new LessonProgress("misha", FAMILY, 3), new LessonProgress("ivan", FAMILY, 7)));
    }

    /** Порядок как в прототипе: новые задания сверху — {@code order} больше, значит создано позже. */
    private static Task task(String id, String childId, String title, String reward, TaskStatus status, Instant now, int order) {
        return new Task(id, FAMILY, childId, title, Money.of(reward), status, now.minus(Duration.ofHours(10L - order)));
    }
}
