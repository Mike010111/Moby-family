package by.moby.payment.p2p;

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
import by.moby.payment.account.SystemAccounts;
import by.moby.payment.catalog.Categories;
import by.moby.payment.catalog.Contact;
import by.moby.payment.catalog.DemoCatalog;
import by.moby.payment.limits.LimitMath;
import by.moby.payment.limits.LimitService;
import by.moby.payment.platform.idempotency.IdempotencyService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** P2P-переводы подростка: котировка, перевод, разовые одобрения сверх лимита. */
@Service
public class P2pService {

    private final ChildAccounts accounts;
    private final LimitService limits;
    private final P2pApprovalRepository approvals;
    private final AccountOperationRepository operations;
    private final Ledger ledger;
    private final IdempotencyService idempotency;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public P2pService(ChildAccounts accounts, LimitService limits, P2pApprovalRepository approvals, AccountOperationRepository operations,
                      Ledger ledger, IdempotencyService idempotency, ApplicationEventPublisher events, Clock clock) {
        this.accounts = accounts;
        this.limits = limits;
        this.approvals = approvals;
        this.operations = operations;
        this.ledger = ledger;
        this.idempotency = idempotency;
        this.events = events;
        this.clock = clock;
    }

    public record SendResult(P2pQuote.Mode mode, String operationId, Money amount, Money balance) {
    }

    private record SendRequest(String contactId, String amount) {
    }

    @Transactional(readOnly = true)
    public P2pQuote quote(String childId, String rawAmount, String contactId) {
        Account child = accounts.readableChild(childId);
        P2pQuote quote = quote(child, Money.parse(rawAmount).orElse(null));
        return contactId == null ? quote : quote.withContact(DemoCatalog.contact(contactId).map(Contact::name).orElse(null));
    }

    /** Порт {@code quoteP2P}; {@code amount == null} — сумма не распознана. */
    P2pQuote quote(Account child, Money amount) {
        Money balance = child.getBalance();
        if (child.isFrozen()) {
            return P2pQuote.blocked(ErrorCode.FROZEN, "Счёт заблокирован", balance);
        }
        if (!child.getCohort().p2pAllowed()) {
            return P2pQuote.blocked(ErrorCode.AGE, "Переводы доступны с 14 лет", balance);
        }
        if (!child.isAllowed(Categories.P2P)) {
            return P2pQuote.blocked(ErrorCode.PARENT_P2P, "Переводы запрещены", balance);
        }
        if (amount == null || !amount.isPositive()) {
            return P2pQuote.blocked(ErrorCode.AMOUNT, "Укажите сумму — не больше двух знаков после запятой", balance);
        }
        Optional<LimitMath.Tight> tight = limits.tightest(child, new LimitMath.Spend(Categories.P2P_MCC, false, amount));
        Money remaining = tight.map(t -> t.usage().remaining()).orElse(null);
        String limitName = tight.map(t -> limits.name(t.limit())).orElse(null);
        boolean withinLimit = tight.isEmpty() || !amount.isGreaterThan(remaining);
        Optional<P2pApproval> approval = approvals.findFirstByAccountIdAndStatus(child.getId(), ApprovalStatus.ACTIVE);
        Money approvalMax = approval.map(P2pApproval::getMaxAmount).orElse(null);

        if (withinLimit) {
            return child.hasFunds(amount)
                    ? new P2pQuote(P2pQuote.Mode.SEND, true, null, null, remaining, limitName, approvalMax, balance, null)
                    : P2pQuote.blocked(ErrorCode.FUNDS, "Недостаточно средств", balance);
        }
        if (approval.isPresent() && approval.get().covers(amount)) {
            return child.hasFunds(amount)
                    ? new P2pQuote(P2pQuote.Mode.APPROVED, true, null, null, remaining, limitName, approvalMax, balance, null)
                    : P2pQuote.blocked(ErrorCode.FUNDS, "Недостаточно средств", balance);
        }
        return new P2pQuote(P2pQuote.Mode.REQUEST, false, ErrorCode.NEEDS_APPROVAL,
                "Сумма выше лимита: доступно " + remaining.format(), remaining, limitName, approvalMax, balance, null);
    }

    @Transactional
    public SendResult send(String childId, String contactId, String rawAmount, String idempotencyKey) {
        return idempotency.execute(childId, idempotencyKey, "p2p-transfer", new SendRequest(contactId, rawAmount), SendResult.class, () -> {
            Contact contact = DemoCatalog.contact(contactId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION, "Выберите получателя"));
            Account child = accounts.lock(accounts.child(childId));
            Money amount = Money.parse(rawAmount).orElse(null);
            P2pQuote q = quote(child, amount);
            if (q.mode() == P2pQuote.Mode.BLOCKED || q.mode() == P2pQuote.Mode.REQUEST) {
                throw new BusinessException(q.code(), q.reason()).with("mode", q.mode());
            }
            String note = null;
            if (q.mode() == P2pQuote.Mode.APPROVED) {
                approvals.findFirstByAccountIdAndStatus(child.getId(), ApprovalStatus.ACTIVE).orElseThrow().use(amount);
                note = "Сверх лимита · одобрено " + accounts.parentOfFamily(child.getFamilyId()).getHolder().dative();
            }
            Instant now = Instant.now(clock);
            String title = "Перевод: " + contact.name();
            LedgerTransaction tx = ledger.transfer(LedgerKind.P2P, title, child, accounts.system(SystemAccounts.P2P_CLEARING), amount);
            AccountOperation op = operations.save(AccountOperation.completed(child.getId(), title, amount.negate(), Categories.P2P_MCC,
                    false, note, now, tx));
            events.publishEvent(new TransferCompleted(UUID.randomUUID(), now, child.getFamilyId(), childId, tx.getId().toString(),
                    TransferCompleted.Kind.P2P, amount, title));
            return new SendResult(q.mode(), op.getId(), amount, child.getBalance());
        });
    }

    /**
     * Родитель одобрил запрос {@code p2p-over}: новое разовое одобрение заменяет прежнее активное.
     * Повтор с тем же {@code requestId} возвращает уже созданное одобрение.
     */
    @Transactional
    public P2pApproval grant(String childId, String requestId, Money max) {
        Account child = accounts.lock(accounts.managedChild(childId));
        Optional<P2pApproval> existing = approvals.findByRequestId(requestId);
        if (existing.isPresent()) {
            return existing.get();
        }
        approvals.findByAccountIdAndStatus(child.getId(), ApprovalStatus.ACTIVE).forEach(P2pApproval::replace);
        approvals.flush(); // частичный уникальный индекс «одно ACTIVE на счёт»: сначала UPDATE, потом INSERT
        return approvals.save(new P2pApproval(child.getId(), requestId, max, Instant.now(clock)));
    }
}
