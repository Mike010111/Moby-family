package by.moby.family.payment;

import by.moby.common.error.BusinessException;
import by.moby.common.error.ErrorCode;
import by.moby.common.money.Money;
import by.moby.family.platform.security.CurrentUserProvider;
import by.moby.family.platform.web.IdempotencyKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * RestClient к payment с ретрансляцией JWT пользователя: payment проверяет права сам.
 * ProblemDetail payment превращается в BusinessException с тем же {@code code} и {@code detail};
 * сетевые ошибки и 5xx — в {@code UNAVAILABLE}.
 */
@Component
class HttpPaymentClient implements PaymentClient {

    private static final Logger log = LoggerFactory.getLogger(HttpPaymentClient.class);

    private final RestClient rest;
    private final CurrentUserProvider users;

    HttpPaymentClient(@Qualifier("paymentRestClient") RestClient rest, CurrentUserProvider users) {
        this.rest = rest;
        this.users = users;
    }

    @Override
    public TransferResult parentToChild(String childId, Money amount, TransferKind kind, String title, String idempotencyKey) {
        return call(() -> rest.post().uri("/api/payment/internal/transfers/parent-to-child")
                .headers(h -> {
                    h.setBearerAuth(users.get().token());
                    h.set(IdempotencyKey.HEADER, idempotencyKey);
                })
                .body(Map.of("childId", childId, "amount", amount, "kind", kind, "title", title))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw problem(res.getStatusCode(), res.getBody());
                })
                .body(TransferResult.class));
    }

    @Override
    public void grantP2pApproval(String childId, String requestId, Money max) {
        call(() -> rest.post().uri("/api/payment/internal/children/{childId}/p2p-approvals", childId)
                .headers(h -> h.setBearerAuth(users.get().token()))
                .body(Map.of("requestId", requestId, "max", max))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw problem(res.getStatusCode(), res.getBody());
                })
                .toBodilessEntity());
    }

    @Override
    public void openSavings(String childId, String piggyId) {
        call(() -> rest.post().uri("/api/payment/internal/savings")
                .headers(h -> h.setBearerAuth(users.get().token()))
                .body(Map.of("childId", childId, "piggyId", piggyId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw problem(res.getStatusCode(), res.getBody());
                })
                .toBodilessEntity());
    }

    @Override
    public SavingsResult depositToSavings(String piggyId, Money amount, String title, String idempotencyKey) {
        return savings("/api/payment/internal/savings/{piggyId}/deposit", piggyId, Map.of("amount", amount, "title", title), idempotencyKey);
    }

    @Override
    public SavingsResult withdrawSavings(String piggyId, String title, String idempotencyKey) {
        return savings("/api/payment/internal/savings/{piggyId}/withdraw", piggyId, Map.of("title", title), idempotencyKey);
    }

    @Override
    public Quote quoteP2p(String childId, String amount, String contactId) {
        return call(() -> rest.get()
                .uri("/api/payment/children/{childId}/p2p/quote?amount={amount}&contactId={contactId}", childId,
                        amount == null ? "" : amount, contactId == null ? "" : contactId)
                .headers(h -> h.setBearerAuth(users.get().token()))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw problem(res.getStatusCode(), res.getBody());
                })
                .body(Quote.class));
    }

    private SavingsResult savings(String uri, String piggyId, Map<String, Object> body, String idempotencyKey) {
        return call(() -> rest.post().uri(uri, piggyId)
                .headers(h -> {
                    h.setBearerAuth(users.get().token());
                    h.set(IdempotencyKey.HEADER, idempotencyKey);
                })
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw problem(res.getStatusCode(), res.getBody());
                })
                .body(SavingsResult.class));
    }

    private <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            log.warn("payment call failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.UNAVAILABLE, "Платёжный сервис временно недоступен — попробуйте ещё раз");
        }
    }

    private BusinessException problem(HttpStatusCode status, InputStream body) {
        Optional<PaymentProblems.Problem> problem = Optional.empty();
        try {
            problem = PaymentProblems.read(body);
        } catch (RuntimeException | IOException e) {
            log.warn("unreadable payment error body ({}): {}", status, e.getMessage());
        }
        Optional<ErrorCode> code = problem.flatMap(p -> Arrays.stream(ErrorCode.values()).filter(c -> c.name().equals(p.code())).findFirst());
        if (status.is5xxServerError() || code.isEmpty()) {
            return new BusinessException(ErrorCode.UNAVAILABLE, "Платёжный сервис временно недоступен — попробуйте ещё раз");
        }
        return new BusinessException(code.get(), problem.get().detail());
    }
}
