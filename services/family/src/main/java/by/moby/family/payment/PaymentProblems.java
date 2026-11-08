package by.moby.family.payment;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/** Чтение ProblemDetail (RFC 9457) из тела ошибки payment: нужны только {@code code} и {@code detail}. */
final class PaymentProblems {

    record Problem(String code, String detail) {
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private PaymentProblems() {
    }

    static Optional<Problem> read(InputStream body) throws IOException {
        JsonNode node = JSON.readTree(body);
        if (node == null || !node.hasNonNull("code")) {
            return Optional.empty();
        }
        return Optional.of(new Problem(node.get("code").asString(), node.path("detail").asString(null)));
    }
}
