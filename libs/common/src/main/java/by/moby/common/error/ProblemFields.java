package by.moby.common.error;

/** Имена расширений ProblemDetail, на которые опираются клиенты (фронтенд, другие сервисы). */
public final class ProblemFields {

    /** Машиночитаемый код отказа — значение {@link ErrorCode}. */
    public static final String CODE = "code";

    private ProblemFields() {
    }
}
