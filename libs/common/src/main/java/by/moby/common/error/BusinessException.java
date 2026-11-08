package by.moby.common.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Отказ по бизнес-правилу. Сервисы превращают его в ProblemDetail с полем {@code code};
 * {@link #details()} становятся дополнительными полями ответа (например, {@code merchant}, {@code mode}).
 * {@code reason} — человекочитаемый текст для пользователя (как {@code reason} в прототипе).
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, Object> details = new LinkedHashMap<>();

    public BusinessException(ErrorCode code, String reason) {
        super(reason);
        this.code = code;
    }

    public BusinessException with(String key, Object value) {
        details.put(key, value);
        return this;
    }

    public ErrorCode code() {
        return code;
    }

    public String reason() {
        return getMessage();
    }

    public Map<String, Object> details() {
        return Collections.unmodifiableMap(details);
    }
}
