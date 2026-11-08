package by.moby.common.error;

/**
 * Отказ по бизнес-правилу. Сервисы превращают его в ProblemDetail с полем {@code code}.
 * {@code reason} — человекочитаемый текст для пользователя (как {@code reason} в прототипе).
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode code;

    public BusinessException(ErrorCode code, String reason) {
        super(reason);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }

    public String reason() {
        return getMessage();
    }
}
