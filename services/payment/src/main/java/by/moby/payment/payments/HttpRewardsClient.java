package by.moby.payment.payments;

import by.moby.common.money.Money;
import by.moby.payment.platform.security.CurrentUserProvider;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * HTTP-клиент rewards. JWT пользователя ретранслируется: rewards сам проверяет, что ребёнок
 * спрашивает про себя. Таймауты — в {@link RewardsClientConfig}.
 */
@Component
class HttpRewardsClient implements RewardsClient {

    private static final Logger log = LoggerFactory.getLogger(HttpRewardsClient.class);

    private final RestClient rest;
    private final CurrentUserProvider users;
    private final MeterRegistry meters;

    HttpRewardsClient(@Qualifier("rewardsRestClient") RestClient rest, CurrentUserProvider users, MeterRegistry meters) {
        this.rest = rest;
        this.users = users;
        this.meters = meters;
    }

    @Override
    public Perks perks(String childId, String category, Money amount) {
        try {
            Perks perks = rest.get()
                    .uri("/api/rewards/children/{childId}/payment-perks?category={category}&amount={amount}",
                            childId, category, amount.amount().toPlainString())
                    .headers(h -> h.setBearerAuth(users.get().token()))
                    .retrieve()
                    .body(Perks.class);
            return perks == null ? Perks.unknown() : perks;
        } catch (RestClientException e) {
            log.warn("rewards unavailable, paying without cashback: {}", e.getMessage());
            meters.counter("moby.rewards.perks.failures").increment();
            return Perks.unknown();
        }
    }
}
