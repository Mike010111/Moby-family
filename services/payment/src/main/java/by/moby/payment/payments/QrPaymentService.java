package by.moby.payment.payments;

import by.moby.common.error.BusinessException;
import by.moby.common.error.ErrorCode;
import by.moby.common.events.CashbackGranted;
import by.moby.common.events.PaymentCompleted;
import by.moby.common.events.PaymentDeclined;
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
import by.moby.payment.catalog.Cohort;
import by.moby.payment.catalog.DemoCatalog;
import by.moby.payment.catalog.Merchant;
import by.moby.payment.limits.LimitMath;
import by.moby.payment.limits.LimitService;
import by.moby.payment.platform.idempotency.IdempotencyService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Оплата по QR. Порядок: запрос перков у rewards (до блокировки, чтобы не держать строку счёта
 * во время сетевого вызова) → транзакция: ключ идемпотентности → {@code SELECT … FOR UPDATE}
 * по счёту → проверки {@link PaymentPolicy} → проводки → события в outbox.
 */
@Service
public class QrPaymentService {

    private final ChildAccounts accounts;
    private final AccountOperationRepository operations;
    private final LimitService limits;
    private final Ledger ledger;
    private final RewardsClient rewards;
    private final IdempotencyService idempotency;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final MeterRegistry meters;
    private final Clock clock;

    public QrPaymentService(ChildAccounts accounts, AccountOperationRepository operations, LimitService limits, Ledger ledger,
                            RewardsClient rewards, IdempotencyService idempotency, ApplicationEventPublisher events,
                            TransactionTemplate tx, MeterRegistry meters, Clock clock) {
        this.accounts = accounts;
        this.operations = operations;
        this.limits = limits;
        this.ledger = ledger;
        this.rewards = rewards;
        this.idempotency = idempotency;
        this.events = events;
        this.tx = tx;
        this.meters = meters;
        this.clock = clock;
    }

    private record Request(String childId, String merchantId) {
    }

    public QrPaymentResult pay(String childId, String merchantId, String idempotencyKey) {
        Account snapshot = accounts.child(childId);
        Merchant merchant = DemoCatalog.merchant(snapshot.getCohort(), merchantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "QR-код не распознан"));
        Perks perks = rewards.perks(childId, Categories.groupOf(merchant.mcc()), merchant.amount());

        return tx.execute(status -> idempotency.execute(childId, idempotencyKey, "qr-payment", new Request(childId, merchantId),
                QrPaymentResult.class, () -> execute(childId, merchant, perks)));
    }

    private QrPaymentResult execute(String childId, Merchant merchant, Perks perks) {
        Account child = accounts.lock(accounts.child(childId));
        Instant now = Instant.now(clock);
        LimitMath.Spend spend = new LimitMath.Spend(merchant.mcc(), false, merchant.amount());
        Optional<BusinessException> decline = PaymentPolicy.check(child, spend,
                () -> limits.tightest(child, spend), t -> limits.name(t.limit()));
        if (decline.isPresent()) {
            return declined(child, merchant, decline.get(), now);
        }

        Account merchants = accounts.system(SystemAccounts.MERCHANTS);
        LedgerTransaction payment = ledger.transfer(LedgerKind.QR_PAYMENT, merchant.name(), child, merchants, merchant.amount());
        String legal = child.getCohort() == Cohort.JUNIOR ? "Мелкая бытовая сделка · ст. 27 ГК РБ" : "QR КРОК";
        AccountOperation op = operations.save(AccountOperation.completed(child.getId(), merchant.name(), merchant.amount().negate(),
                merchant.mcc(), false, legal, now, payment));

        QrPaymentResult.Cashback cashback = null;
        PaymentCompleted.Cashback eventCashback = null;
        if (perks.cashback() != null) {
            Perks.Cashback c = perks.cashback();
            Money value = cashbackAmount(merchant.amount(), c.percent());
            Account bank = accounts.system(SystemAccounts.CASHBACK);
            LedgerTransaction credit = ledger.transfer(LedgerKind.CASHBACK, "Кэшбэк " + c.artifactName(), bank, child, value);
            operations.save(AccountOperation.completed(child.getId(), "Кэшбэк " + c.percent() + "% · " + c.artifactName(), value,
                    Categories.CASHBACK, false, Categories.groupLabel(merchant.mcc()), now.plusMillis(1), credit));
            cashback = new QrPaymentResult.Cashback(value, c.percent(), c.artifactName());
            eventCashback = new PaymentCompleted.Cashback(c.percent(), value, c.artifactId(), c.artifactName());
            events.publishEvent(new CashbackGranted(UUID.randomUUID(), now, child.getFamilyId(), childId, op.getId(),
                    c.artifactId(), c.artifactName(), c.percent(), value));
        }
        events.publishEvent(new PaymentCompleted(UUID.randomUUID(), now, child.getFamilyId(), childId, child.getHolder().name(),
                child.getHolder().female(), op.getId(), merchant.name(), merchant.amount(), merchant.mcc(),
                Categories.groupOf(merchant.mcc()), Categories.groupLabel(merchant.mcc()), eventCashback));
        meters.counter("moby.payments.completed").increment();
        return new QrPaymentResult(QrPaymentResult.Status.COMPLETED, op.getId(), merchant, perks.points(), legal, cashback,
                child.getBalance(), null, null);
    }

    private QrPaymentResult declined(Account child, Merchant merchant, BusinessException reason, Instant now) {
        AccountOperation op = operations.save(AccountOperation.declined(child.getId(), merchant.name(), merchant.amount().negate(),
                merchant.mcc(), reason.code(), reason.reason(), now));
        events.publishEvent(new PaymentDeclined(UUID.randomUUID(), now, child.getFamilyId(), child.getOwnerId(), child.getHolder().name(),
                op.getId(), merchant.name(), merchant.amount(), merchant.mcc(), reason.code(), reason.reason()));
        meters.counter("moby.payments.declined", "code", reason.code().name()).increment();
        return new QrPaymentResult(QrPaymentResult.Status.DECLINED, op.getId(), merchant, null, null, null,
                child.getBalance(), reason.code(), reason.reason());
    }

    /** {@code cashbackAmount}: процент от покупки, не меньше 1 копейки. */
    static Money cashbackAmount(Money amount, int percent) {
        BigDecimal raw = amount.amount().multiply(BigDecimal.valueOf(percent)).divide(BigDecimal.valueOf(100));
        return Money.rounded(raw.max(new BigDecimal("0.01")));
    }
}
