package by.moby.payment.transfers;

import by.moby.common.error.BusinessException;
import by.moby.common.error.ErrorCode;
import by.moby.common.events.TransferCompleted;
import by.moby.common.money.Money;
import by.moby.payment.account.Account;
import by.moby.payment.account.AccountOperation;
import by.moby.payment.account.AccountOperationRepository;
import by.moby.payment.account.ChildAccounts;
import by.moby.payment.account.Ledger;
import by.moby.payment.account.LedgerKind;
import by.moby.payment.account.LedgerTransaction;
import by.moby.payment.catalog.Categories;
import by.moby.payment.platform.idempotency.IdempotencyService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Переводы главный родственник → ребёнок ({@code transferFromParent}): пополнение, автоперевод,
 * награда за задание, одобренный запрос. Права проверяет вызывающая сторона.
 */
@Service
public class TransferService {

    private final ChildAccounts accounts;
    private final AccountOperationRepository operations;
    private final Ledger ledger;
    private final IdempotencyService idempotency;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public TransferService(ChildAccounts accounts, AccountOperationRepository operations, Ledger ledger,
                           IdempotencyService idempotency, ApplicationEventPublisher events, Clock clock) {
        this.accounts = accounts;
        this.operations = operations;
        this.ledger = ledger;
        this.idempotency = idempotency;
        this.events = events;
        this.clock = clock;
    }

    private record Request(String childId, Money amount, TransferCompleted.Kind kind, String title) {
    }

    /** Перевод с ключом идемпотентности, привязанным к {@code ownerId} (кто инициировал). */
    @Transactional
    public TransferResult parentToChild(String ownerId, String idempotencyKey, Account child, Money amount,
                                        TransferCompleted.Kind kind, String title) {
        return idempotency.execute(ownerId, idempotencyKey, "parent-to-child", new Request(child.getOwnerId(), amount, kind, title),
                TransferResult.class, () -> transfer(child, amount, kind, title));
    }

    private TransferResult transfer(Account childAccount, Money amount, TransferCompleted.Kind kind, String title) {
        if (!amount.isPositive()) {
            throw new BusinessException(ErrorCode.AMOUNT, "Укажите сумму — не больше двух знаков после запятой");
        }
        List<Account> locked = accounts.lock(accounts.parentOfFamily(childAccount.getFamilyId()), childAccount);
        Account parent = locked.get(0);
        Account child = locked.get(1);
        if (child.isFrozen()) {
            throw new BusinessException(ErrorCode.FROZEN, "Счёт заблокирован");
        }
        if (!parent.hasFunds(amount)) {
            throw new BusinessException(ErrorCode.FUNDS, "Недостаточно средств на счёте");
        }
        Instant now = Instant.now(clock);
        LedgerTransaction tx = ledger.transfer(LedgerKind.valueOf(kind.name()), title, parent, child, amount);
        operations.save(AccountOperation.completed(child.getId(), title, amount, Categories.TOPUP, false, null, now, tx));
        events.publishEvent(new TransferCompleted(UUID.randomUUID(), now, child.getFamilyId(), child.getOwnerId(),
                tx.getId().toString(), kind, amount, title));
        return new TransferResult(tx.getId().toString(), amount, child.getBalance(), parent.getBalance());
    }
}
