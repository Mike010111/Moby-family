package by.moby.payment.platform.idempotency;

import by.moby.common.error.BusinessException;
import by.moby.common.error.ErrorCode;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.function.Supplier;

/**
 * Идемпотентность денежных операций по заголовку {@code Idempotency-Key}.
 *
 * <p>Ключ вставляется в той же транзакции, что и сама операция. Уникальный индекс
 * {@code (owner_id, idem_key)} сериализует конкурентные повторы: второй запрос ждёт коммита первого
 * и получает сохранённый ответ. Если операция отклонена исключением, транзакция откатывается вместе
 * с ключом — повтор выполнится заново (операция ничего не изменила, ответ будет тем же).
 */
@Service
public class IdempotencyService {

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final Clock clock;

    public IdempotencyService(JdbcClient jdbc, JsonMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public <T> T execute(String ownerId, String key, String operation, Object request, Class<T> responseType, Supplier<T> action) {
        if (key == null || key.isBlank() || key.length() > 100) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY, "Нужен заголовок Idempotency-Key (до 100 символов)");
        }
        String hash = hash(operation, request);
        int inserted = jdbc.sql("""
                        insert into idempotency_records (owner_id, idem_key, operation, request_hash, created_at)
                        values (:owner, :key, :operation, :hash, :createdAt)
                        on conflict (owner_id, idem_key) do nothing""")
                .param("owner", ownerId)
                .param("key", key)
                .param("operation", operation)
                .param("hash", hash)
                .param("createdAt", java.sql.Timestamp.from(Instant.now(clock)))
                .update();
        if (inserted == 0) {
            return replay(ownerId, key, operation, hash, responseType);
        }
        T result = action.get();
        jdbc.sql("update idempotency_records set response_body = cast(:body as jsonb) where owner_id = :owner and idem_key = :key")
                .param("body", json.writeValueAsString(result))
                .param("owner", ownerId)
                .param("key", key)
                .update();
        return result;
    }

    private <T> T replay(String ownerId, String key, String operation, String hash, Class<T> responseType) {
        var stored = jdbc.sql("select operation, request_hash, response_body::text as body from idempotency_records where owner_id = :owner and idem_key = :key")
                .param("owner", ownerId)
                .param("key", key)
                .query((rs, i) -> new Stored(rs.getString("operation"), rs.getString("request_hash"), rs.getString("body")))
                .single();
        if (!stored.operation().equals(operation) || !stored.hash().equals(hash) || stored.body() == null) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_MISMATCH, "Ключ идемпотентности уже использован для другого запроса");
        }
        return json.readValue(stored.body(), responseType);
    }

    private String hash(String operation, Object request) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((operation + ':' + json.writeValueAsString(request)).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private record Stored(String operation, String hash, String body) {
    }
}
