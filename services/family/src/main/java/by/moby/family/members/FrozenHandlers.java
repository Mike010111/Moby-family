package by.moby.family.members;

import by.moby.common.events.AccountFrozen;
import by.moby.common.events.AccountUnfrozen;
import by.moby.family.platform.messaging.EventHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Проекция заморозки счёта из событий payment. */
@Configuration(proxyBeanMethods = false)
class FrozenHandlers {

    @Bean
    EventHandler<AccountFrozen> accountFrozenHandler(FrozenProjection projection) {
        return new EventHandler<>() {
            @Override
            public Class<AccountFrozen> type() {
                return AccountFrozen.class;
            }

            @Override
            public void handle(AccountFrozen event) {
                projection.apply(event.childId(), true, event.occurredAt());
            }
        };
    }

    @Bean
    EventHandler<AccountUnfrozen> accountUnfrozenHandler(FrozenProjection projection) {
        return new EventHandler<>() {
            @Override
            public Class<AccountUnfrozen> type() {
                return AccountUnfrozen.class;
            }

            @Override
            public void handle(AccountUnfrozen event) {
                projection.apply(event.childId(), false, event.occurredAt());
            }
        };
    }
}
