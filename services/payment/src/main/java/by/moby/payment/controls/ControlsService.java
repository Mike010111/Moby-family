package by.moby.payment.controls;

import by.moby.common.error.BusinessException;
import by.moby.common.error.ErrorCode;
import by.moby.common.events.AccountFrozen;
import by.moby.common.events.AccountUnfrozen;
import by.moby.common.events.Actor;
import by.moby.payment.account.Account;
import by.moby.payment.account.ChildAccounts;
import by.moby.payment.account.Holder;
import by.moby.payment.catalog.Categories;
import by.moby.payment.platform.security.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Родительский контроль: заморозка счёта ({@code parent/toggleFreeze}) и тумблеры категорий ({@code parent/toggleAllow}). */
@Service
public class ControlsService {

    private final ChildAccounts accounts;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ControlsService(ChildAccounts accounts, ApplicationEventPublisher events, Clock clock) {
        this.accounts = accounts;
        this.events = events;
        this.clock = clock;
    }

    /** Повторная установка того же состояния — без событий. */
    @Transactional
    public Account setFrozen(String childId, boolean frozen) {
        Account child = accounts.lock(accounts.managedChild(childId));
        boolean changed = frozen ? child.freeze() : child.unfreeze();
        if (changed) {
            CurrentUser user = accounts.currentUser();
            Holder parent = accounts.parentOfFamily(child.getFamilyId()).getHolder();
            Actor actor = new Actor(user.memberId(), parent.label(), parent.female());
            Instant now = Instant.now(clock);
            String name = child.getHolder().name();
            Object event = frozen
                    ? new AccountFrozen(UUID.randomUUID(), now, child.getFamilyId(), childId, name, child.getCard().getTitle(), child.getCard().getMask(), actor)
                    : new AccountUnfrozen(UUID.randomUUID(), now, child.getFamilyId(), childId, name, child.getCard().getTitle(), child.getCard().getMask(), actor);
            events.publishEvent(event);
        }
        return child;
    }

    @Transactional
    public Account setCategory(String childId, String key, boolean allowed) {
        Account child = accounts.managedChild(childId);
        if (!Categories.availableFor(child.getCohort(), key)) {
            throw Categories.P2P.equals(key)
                    ? new BusinessException(ErrorCode.AGE, "Переводы доступны с 14 лет")
                    : new BusinessException(ErrorCode.CATEGORY, "Настройка недоступна");
        }
        child = accounts.lock(child);
        child.setPermission(key, allowed);
        return child;
    }
}
