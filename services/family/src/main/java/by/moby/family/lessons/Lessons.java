package by.moby.family.lessons;

import java.util.List;

/** Уроки финансовой грамотности (LESSONS в catalog.js). */
public final class Lessons {

    public static final List<String> ALL = List.of(
            "Откуда берутся деньги",
            "Наличные и карта",
            "Что такое баланс",
            "Что такое бюджет",
            "Копим на мечту",
            "Безопасность: мошенники",
            "Кешбэк и баллы",
            "Мой первый финансовый план");

    /** Баллы за пройденный урок. */
    public static final int POINTS = 10;

    private Lessons() {
    }
}
