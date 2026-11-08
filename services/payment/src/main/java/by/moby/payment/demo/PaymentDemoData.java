package by.moby.payment.demo;

import by.moby.common.money.Money;
import by.moby.payment.account.Account;
import by.moby.payment.account.AccountKind;
import by.moby.payment.account.AccountOperation;
import by.moby.payment.account.AccountOperationRepository;
import by.moby.payment.account.AccountRepository;
import by.moby.payment.account.CardDetails;
import by.moby.payment.account.Holder;
import by.moby.payment.account.Ledger;
import by.moby.payment.account.LedgerKind;
import by.moby.payment.account.SystemAccounts;
import by.moby.payment.autopay.AutopayEvery;
import by.moby.payment.autopay.AutopayRule;
import by.moby.payment.autopay.AutopayRuleRepository;
import by.moby.payment.catalog.Cohort;
import by.moby.payment.limits.LimitDraft;
import by.moby.payment.platform.time.TimeConfig;
import by.moby.payment.limits.LimitPeriods;
import by.moby.payment.limits.LimitUnit;
import by.moby.payment.limits.SpendingLimit;
import by.moby.payment.limits.SpendingLimitRepository;
import by.moby.payment.subscriptions.Subscription;
import by.moby.payment.subscriptions.SubscriptionRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Демо-данные из initial-state.js. Время операций считается от «сейчас» ({@link Clock}),
 * чтобы лимиты и выписка выглядели живыми в любой день показа. Только профиль {@code demo}.
 */
@Component
@Profile("demo")
public class PaymentDemoData {

    public static final String FAMILY = "fam-demo";

    private static final Duration HOUR = Duration.ofHours(1);
    private static final Duration DAY = Duration.ofDays(1);

    private final AccountRepository accounts;
    private final AccountOperationRepository operations;
    private final SpendingLimitRepository limits;
    private final SubscriptionRepository subscriptions;
    private final AutopayRuleRepository autopay;
    private final Ledger ledger;
    private final JdbcClient jdbc;
    private final Clock clock;

    public PaymentDemoData(AccountRepository accounts, AccountOperationRepository operations, SpendingLimitRepository limits,
                           SubscriptionRepository subscriptions, AutopayRuleRepository autopay, Ledger ledger, JdbcClient jdbc, Clock clock) {
        this.accounts = accounts;
        this.operations = operations;
        this.limits = limits;
        this.subscriptions = subscriptions;
        this.autopay = autopay;
        this.ledger = ledger;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public void seedIfEmpty() {
        if (accounts.findByOwnerIdAndKind("misha", AccountKind.CHILD).isEmpty()) {
            seed();
        }
    }

    /** Полный сброс: удаляет клиентские данные и создаёт демо-семью заново. */
    @Transactional
    public void reset() {
        jdbc.sql("""
                truncate idempotency_records, account_operations, ledger_entries, ledger_transactions, spending_limits,
                         p2p_approvals, subscriptions, autopay_rules, category_permissions
                """).update();
        jdbc.sql("delete from accounts where kind <> 'SYSTEM'").update();
        seed();
    }

    private void seed() {
        Instant now = Instant.now(clock);
        Account opening = accounts.findById(SystemAccounts.OPENING).orElseThrow();

        Account mom = accounts.save(Account.parent("acc-mom", FAMILY, "mom", new Holder("Анна", "Мама", "мамы", "маме", true),
                "•• 7710", "Семейный пакет Moby"));
        open(opening, mom, "842.60");

        Account misha = accounts.save(Account.child("acc-misha", FAMILY, "misha", Cohort.JUNIOR, new Holder("Миша", null, null, null, false),
                new CardDetails("Карта учащегося", "•• 7890", "5321 4812 3456 7890", "09/29", "2024101234", "ROMEYKO MIKE",
                        "assets/cards/student_card_kid.webp?v=2")));
        permissions(misha, Map.ofEntries(Map.entry("food", true), Map.entry("cafe", true), Map.entry("fastfood", false),
                Map.entry("transport", true), Map.entry("clothes", false), Map.entry("electronics", false), Map.entry("books", true),
                Map.entry("education", true), Map.entry("sport", true), Map.entry("pharmacy", true), Map.entry("games", false),
                Map.entry("entertainment", true), Map.entry("subscriptions", false), Map.entry("online", false), Map.entry("cash", false)));
        open(opening, misha, "12.40");
        limit("l-m1", misha, 1, LimitUnit.DAY, "15", List.of(), now);
        limit("l-m2", misha, 1, LimitUnit.WEEK, "20", List.of("cafe"), now);
        autopay.save(new AutopayRule(misha.getId(), true, Money.of("10"), AutopayEvery.WEEK, 0, 1, LocalDate.now(clock)));
        history(misha, now, List.of(
                today("x1", "Буфет школы №12", "-2.40", "5462", HOUR.multipliedBy(3), now),
                today("x2", "Канцтовары «Скрепка»", "-1.20", "5943", HOUR.multipliedBy(5), now),
                ago("x3", "Автоперевод от мамы", "10.00", "topup", DAY.multipliedBy(2), now, false),
                ago("x4", "Буфет школы №12", "-2.10", "5462", DAY.multipliedBy(2).plus(HOUR.multipliedBy(4)), now, false),
                ago("x5", "Книжный «Академкнига»", "-6.80", "5942", DAY.multipliedBy(9), now, false)));
        savings(opening, misha, "pg-bike", "86.50");

        Account ivan = accounts.save(Account.child("acc-ivan", FAMILY, "ivan", Cohort.TEEN, new Holder("Иван", null, null, null, false),
                new CardDetails("Карта учащегося", "•• 7891", "5321 4812 3456 7891", "10/30", "2024101235", "IVANOV IVAN",
                        "assets/cards/student_card_teen.webp?v=2")));
        permissions(ivan, Map.ofEntries(Map.entry("food", true), Map.entry("cafe", true), Map.entry("fastfood", true),
                Map.entry("transport", true), Map.entry("clothes", true), Map.entry("electronics", true), Map.entry("books", true),
                Map.entry("education", true), Map.entry("sport", true), Map.entry("pharmacy", true), Map.entry("games", false),
                Map.entry("entertainment", true), Map.entry("subscriptions", true), Map.entry("online", true), Map.entry("cash", true),
                Map.entry("p2p", true)));
        ivan.setStats(1243, "2022");
        open(opening, ivan, "64.20");
        limit("l-a1", ivan, 1, LimitUnit.DAY, "60", List.of(), now);
        limit("l-a2", ivan, 1, LimitUnit.WEEK, "30", List.of("p2p"), now);
        autopay.save(new AutopayRule(ivan.getId(), true, Money.of("40"), AutopayEvery.WEEK, 4, 1, LocalDate.now(clock)));
        subscriptions.saveAll(List.of(
                new Subscription("sub1", ivan.getId(), "Музыкальный стриминг", Money.of("6.99"), "12 ноя", true, "music", 1),
                new Subscription("sub2", ivan.getId(), "Курс английского", Money.of("15"), "3 ноя", true, "grad", 2),
                new Subscription("sub3", ivan.getId(), "Облако 100 ГБ", Money.of("3.49"), "20 ноя", false, "cloud", 3)));
        history(ivan, now, List.of(
                today("y1", "Кофейня «Зерно»", "-6.50", "5812", HOUR.multipliedBy(2), now),
                today("y2", "Перевод: Катя Соколова", "-5.50", "4829", HOUR.multipliedBy(4), now),
                ago("y3", "Музыкальный стриминг", "-6.99", "5735", DAY.multipliedBy(3), now, true),
                ago("y4", "Автоперевод от мамы", "40.00", "topup", DAY.multipliedBy(4), now, false),
                ago("y5", "Книжный «Академкнига»", "-18.40", "5942", DAY.multipliedBy(5), now, false),
                ago("y6", "Такси", "-9.20", "4121", DAY.multipliedBy(12), now, false),
                ago("y7", "Одежда «Mark Formelle»", "-42.00", "5651", DAY.multipliedBy(18), now, false),
                ago("y8", "Курс английского", "-15.00", "8299", DAY.multipliedBy(26), now, true)));
        savings(opening, ivan, "pg-laptop", "310.00");
        savings(opening, ivan, "pg-concert", "52.00");
    }

    private record Row(String id, String title, Money amount, String mcc, Instant ts, boolean online, Duration ago) {

        /** Сегодняшние операции остаются сегодняшними: время «сжимается» в начало дня, порядок сохраняется. */
        Row today(Instant now) {
            Instant dayStart = LimitPeriods.unitStart(now, LimitUnit.DAY, TimeConfig.ZONE);
            if (!ts.isBefore(dayStart)) {
                return this;
            }
            double sinceStart = Duration.between(dayStart, now).toMillis();
            long shifted = (long) (sinceStart * (1 - (double) ago.toMillis() / DAY.toMillis()));
            return new Row(id, title, amount, mcc, dayStart.plusMillis(shifted), online, ago);
        }
    }

    private static Row ago(String id, String title, String amount, String mcc, Duration ago, Instant now, boolean online) {
        return new Row(id, title, Money.of(amount), mcc, now.minus(ago), online, ago);
    }

    private static Row today(String id, String title, String amount, String mcc, Duration ago, Instant now) {
        return ago(id, title, amount, mcc, ago, now, false).today(now);
    }

    private void open(Account opening, Account account, String balance) {
        ledger.transfer(LedgerKind.OPENING, "Входящий остаток", opening, account, Money.of(balance));
    }

    private void savings(Account opening, Account child, String piggyId, String saved) {
        Account s = accounts.save(Account.savings("acc-" + piggyId, FAMILY, child.getOwnerId(), piggyId));
        open(opening, s, saved);
    }

    private void permissions(Account account, Map<String, Boolean> allow) {
        allow.forEach(account::setPermission);
    }

    private void limit(String id, Account account, int count, LimitUnit unit, String amount, List<String> categories, Instant now) {
        limits.save(new SpendingLimit(id, account.getId(), new LimitDraft(count, unit, Money.of(amount), categories),
                LimitPeriods.unitStart(now, unit, clock.getZone())));
    }

    private void history(Account account, Instant now, List<Row> rows) {
        rows.forEach(r -> operations.save(AccountOperation.history(r.id(), account.getId(), r.title(), r.amount(), r.mcc(), r.online(), r.ts())));
    }
}
