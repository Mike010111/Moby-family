package by.moby.common.error;

/**
 * Коды отказов — общий контракт REST (поле {@code code} в ProblemDetail) и событий.
 * Первая группа повторяет коды JS-прототипа; вторая — коды для отказов, которые в прототипе
 * возвращались без кода (см. docs/DECISIONS.md).
 */
public enum ErrorCode {

    // ── коды из rules.js / actions.js
    LIMIT(422, "Превышен лимит"),
    FROZEN(423, "Счёт заблокирован"),
    HARD_BLOCK(422, "Категория запрещена банком"),
    PARENT_CATEGORY(422, "Категория запрещена родителем"),
    PARENT_ONLINE(422, "Покупки в интернете запрещены"),
    PARENT_P2P(422, "Переводы запрещены"),
    FUNDS(422, "Недостаточно средств"),
    AMOUNT(400, "Некорректная сумма"),
    NEEDS_APPROVAL(422, "Нужно одобрение родителя"),
    HIERARCHY(422, "Нарушена иерархия лимитов"),
    DUPLICATE(409, "Такая запись уже есть"),
    COUNT(400, "Некорректный срок"),
    UNIT(400, "Некорректная единица срока"),
    CATEGORY(422, "Категория недоступна"),
    BELOW_SAVED(422, "Меньше уже собранного"),
    PENDING(409, "Уже ждём ответа"),
    SOLDOUT(409, "Уже покупали на этой неделе"),
    EQUIPPED(409, "Артефакт уже у маскота"),
    POINTS(422, "Не хватает баллов"),
    AGE(422, "Недоступно по возрасту"),

    // ── коды для отказов без кода в прототипе
    VALIDATION(400, "Некорректный запрос"),
    NOT_FOUND(404, "Не найдено"),
    ALREADY_PROCESSED(409, "Уже обработано"),
    NOT_NEEDED(422, "Действие не требуется"),
    TOO_LARGE(422, "Слишком большая сумма"),
    PIGGY_MAX(422, "Слишком много копилок"),
    ROOM(422, "Больше, чем осталось до цели"),
    UNCHANGED(422, "Ничего не изменилось"),
    LESSONS_DONE(422, "Все уроки пройдены"),
    NOT_FOR_SALE(422, "Не продаётся"),
    NOTHING_TO_RENEW(422, "Продлевать нечего"),
    IDEMPOTENCY_KEY(400, "Нужен заголовок Idempotency-Key"),
    IDEMPOTENCY_MISMATCH(409, "Ключ идемпотентности использован для другого запроса"),
    FORBIDDEN(403, "Нет доступа"),
    UNAVAILABLE(503, "Сервис временно недоступен");

    private final int httpStatus;
    private final String title;

    ErrorCode(int httpStatus, String title) {
        this.httpStatus = httpStatus;
        this.title = title;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String title() {
        return title;
    }

    /** URI типа проблемы (RFC 9457 §3.1.1). */
    public String typeUri() {
        return "https://moby.by/problems/" + name().toLowerCase().replace('_', '-');
    }
}
