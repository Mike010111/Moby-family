package by.moby.payment.transfers;

import by.moby.common.error.BusinessException;
import by.moby.common.error.ErrorCode;
import by.moby.common.events.TransferCompleted;
import by.moby.common.money.Money;
import by.moby.payment.account.Account;
import by.moby.payment.account.AccountOperation;
import by.moby.payment.account.AccountOperationRepository;
import by.moby.payment.account.AccountRepository;
import by.moby.payment.account.ChildAccounts;
import by.moby.payment.account.Ledger;
import by.moby.payment.account.LedgerKind;
import by.moby.payment.account.LedgerTransaction;
import by.moby.payment.catalog.Categories;
import by.moby.payment.platform.idempotency.IdempotencyService;
import by.moby.payment.platform.security.AccessPolicy;
import by.moby.payment.platform.security.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Деньги копилок: SAVINGS-счёт на каждую копилку family. Пополняет копилку только сам ребёнок;
 * возврат на карту (закрытие копилки) — ребёнок или родитель (одобренное удаление).
 */
@Service
public class SavingsService {

    private final ChildAccounts accounts;
    private final AccountRepository accountRepository;
    private final AccountOperationRepository operations;
    private final Ledger ledger;
    private final IdempotencyService idempotency;
    private final AccessPolicy access;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public SavingsService(ChildAccounts accounts, AccountRepository accountRepository, AccountOperationRepository operations, Ledger ledger,
                          IdempotencyService idempotency, AccessPolicy access, ApplicationEventPublisher events, Clock clock) {
        this.accounts = accounts;
        this.accountRepository = accountRepository;
        this.operations = operations;
        this.ledger = ledger;
        this.idempotency = idempotency;
        this.access = access;
        this.events = events;
        this.clock = clock;
    }

    public record SavingsResult(String piggyId, Money amount, Money saved, Money childBalance) {
    }

    private record Request(String piggyId, Money amount, String title) {
    }

    /** Открывает SAVINGS-счёт копилки; повторный вызов возвращает существующий. */
    @Transactional
    public SavingsResult open(String childId, String piggyId) {
        Account child = accounts.readableChild(childId);
        Account savings = accountRepository.findByExternalRefAndKind(piggyId, by.moby.payment.account.AccountKind.SAVINGS)
                .orElseGet(() -> accountRepository.save(Account.savings(UUID.randomUUID().toString(), child.getFamilyId(), childId, piggyId)));
        if (!savings.getOwnerId().equals(childId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Счёт копилки не найден");
        }
        return new SavingsResult(piggyId, Money.ZERO, savings.getBalance(), child.getBalance());
    }

    @Transactional
    public SavingsResult deposit(String piggyId, Money amount, String title, String idempotencyKey) {
        Account savings = accounts.savings(piggyId);
        CurrentUser user = access.requireSelf(savings.getOwnerId());
        return idempotency.execute(user.memberId(), idempotencyKey, "savings-deposit", new Request(piggyId, amount, title),
                SavingsResult.class, () -> {
                    if (!amount.isPositive()) {
                        throw new BusinessException(ErrorCode.AMOUNT, "Укажите сумму — не больше двух знаков после запятой");
                    }
                    List<Account> locked = accounts.lock(accounts.child(savings.getOwnerId()), savings);
                    Account child = locked.get(0);
                    Account piggy = locked.get(1);
                    if (child.isFrozen()) {
                        throw new BusinessException(ErrorCode.FROZEN, "Счёт заблокирован");
                    }
                    if (!child.hasFunds(amount)) {
                        throw new BusinessException(ErrorCode.FUNDS, "Не хватает денег на карте");
                    }
                    LedgerTransaction tx = ledger.transfer(LedgerKind.SAVINGS_DEPOSIT, title, child, piggy, amount);
                    record(child, tx, title, amount.negate(), TransferCompleted.Kind.SAVINGS_DEPOSIT, amount);
                    return new SavingsResult(piggyId, amount, piggy.getBalance(), child.getBalance());
                });
    }

    /** Всё накопленное — на карту ребёнка. */
    @Transactional
    public SavingsResult withdrawAll(String piggyId, String title, String idempotencyKey) {
        Account savings = accounts.savings(piggyId);
        CurrentUser user = access.requireChildOrParent(savings.getFamilyId(), savings.getOwnerId());
        return idempotency.execute(user.memberId(), idempotencyKey, "savings-withdraw", new Request(piggyId, null, title),
                SavingsResult.class, () -> {
                    List<Account> locked = accounts.lock(accounts.child(savings.getOwnerId()), savings);
                    Account child = locked.get(0);
                    Account piggy = locked.get(1);
                    if (child.isFrozen()) {
                        throw new BusinessException(ErrorCode.FROZEN, "Счёт заблокирован");
                    }
                    Money amount = piggy.getBalance();
                    if (amount.isPositive()) {
                        LedgerTransaction tx = ledger.transfer(LedgerKind.SAVINGS_WITHDRAW, title, piggy, child, amount);
                        record(child, tx, title, amount, TransferCompleted.Kind.SAVINGS_WITHDRAW, amount);
                    }
                    return new SavingsResult(piggyId, amount, piggy.getBalance(), child.getBalance());
                });
    }

    private void record(Account child, LedgerTransaction tx, String title, Money signed,
                        TransferCompleted.Kind kind, Money amount) {
        Instant now = Instant.now(clock);
        operations.save(AccountOperation.completed(child.getId(), title, signed, Categories.PIGGY, false, null, now, tx));
        events.publishEvent(new TransferCompleted(UUID.randomUUID(), now, child.getFamilyId(), child.getOwnerId(),
                tx.getId().toString(), kind, amount, title));
    }
}
