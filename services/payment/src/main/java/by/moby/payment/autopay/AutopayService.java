package by.moby.payment.autopay;

import by.moby.common.error.BusinessException;
import by.moby.common.error.ErrorCode;
import by.moby.common.events.TransferCompleted;
import by.moby.common.money.Money;
import by.moby.payment.account.Account;
import by.moby.payment.account.AccountRepository;
import by.moby.payment.account.ChildAccounts;
import by.moby.payment.transfers.TransferService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

@Service
public class AutopayService {

    private static final Logger log = LoggerFactory.getLogger(AutopayService.class);

    private final AutopayRuleRepository rules;
    private final ChildAccounts accounts;
    private final AccountRepository accountRepository;
    private final TransferService transfers;
    private final TransactionTemplate tx;
    private final Clock clock;

    public AutopayService(AutopayRuleRepository rules, ChildAccounts accounts, AccountRepository accountRepository,
                          TransferService transfers, TransactionTemplate tx, Clock clock) {
        this.rules = rules;
        this.accounts = accounts;
        this.accountRepository = accountRepository;
        this.transfers = transfers;
        this.tx = tx;
        this.clock = clock;
    }

    public record AutopayInput(Boolean enabled, String amount, String every, Integer weekday, Integer monthDay) {
    }

    public Optional<AutopayRule> of(Account account) {
        return rules.findById(account.getId());
    }

    /** {@code autopay/update} — валидация как в прототипе. */
    @Transactional
    public AutopayRule update(String childId, AutopayInput input) {
        Account child = accounts.managedChild(childId);
        Money amount = Money.parse(input.amount()).filter(Money::isPositive)
                .orElseThrow(() -> new BusinessException(ErrorCode.AMOUNT, "Укажите сумму — не больше двух знаков после запятой"));
        AutopayEvery every = AutopayEvery.fromKey(input.every())
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION, "Выберите периодичность"));
        if (every == AutopayEvery.WEEK && (input.weekday() == null || input.weekday() < 0 || input.weekday() > 6)) {
            throw new BusinessException(ErrorCode.VALIDATION, "Выберите день недели");
        }
        if (every == AutopayEvery.MONTH && (input.monthDay() == null || input.monthDay() < 1 || input.monthDay() > 28)) {
            throw new BusinessException(ErrorCode.VALIDATION, "Число месяца — от 1 до 28");
        }
        boolean enabled = Boolean.TRUE.equals(input.enabled());
        AutopayRule rule = rules.findById(child.getId())
                .orElseGet(() -> rules.save(new AutopayRule(child.getId(), enabled, amount, every, 0, 1, null)));
        rule.update(enabled, amount, every, input.weekday(), input.monthDay());
        return rule;
    }

    /**
     * Исполнение по расписанию. Перевод и отметка «сегодня выполнено» — отдельные транзакции;
     * ключ идемпотентности {@code autopay:<счёт>:<дата>} гарантирует не больше одного перевода в день,
     * даже если отметка не сохранилась и правило будет обработано снова.
     * Неуспешный перевод (заморожен счёт, нет денег) в тот же день не повторяется.
     *
     * @return сколько переводов выполнено
     */
    public int runDue() {
        LocalDate today = LocalDate.now(clock);
        int done = 0;
        for (AutopayRule rule : rules.findByEnabledTrue()) {
            if (rule.isDue(today)) {
                done += runOne(rule, today) ? 1 : 0;
                tx.executeWithoutResult(status -> rules.findById(rule.getAccountId()).ifPresent(r -> r.markRun(today)));
            }
        }
        return done;
    }

    private boolean runOne(AutopayRule rule, LocalDate today) {
        Account child = accountRepository.findById(rule.getAccountId()).orElseThrow();
        String title = "Автоперевод от " + accounts.parentOfFamily(child.getFamilyId()).getHolder().genitive();
        try {
            transfers.parentToChild("autopay", "autopay:" + child.getId() + ":" + today, child, rule.getAmount(),
                    TransferCompleted.Kind.AUTOPAY, title);
            return true;
        } catch (BusinessException e) {
            log.info("autopay for {} skipped: {} {}", child.getOwnerId(), e.code(), e.reason());
            return false;
        }
    }
}
