package by.moby.family.piggies;

import by.moby.common.error.BusinessException;
import by.moby.common.error.ErrorCode;
import by.moby.common.events.PiggyDeposited;
import by.moby.common.events.RequestKind;
import by.moby.common.money.Money;
import by.moby.family.members.Cohort;
import by.moby.family.members.Member;
import by.moby.family.members.Members;
import by.moby.family.payment.PaymentClient;
import by.moby.family.requests.RequestService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Копилки ({@code piggy/*}). 7–13: изменение и удаление — только через одобрение главного родственника;
 * 14–17 — сразу. Собранная цель закрывает копилку и возвращает деньги на счёт.
 */
@Service
public class PiggyService {

    static final int MAX_PIGGIES = 5;
    /** Баллы за пополнение копилки. */
    public static final int DEPOSIT_POINTS = 2;

    private final PiggyRepository piggies;
    private final PiggyFunds funds;
    private final Members members;
    private final PaymentClient payment;
    private final RequestService requests;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public PiggyService(PiggyRepository piggies, PiggyFunds funds, Members members, PaymentClient payment, RequestService requests,
                        ApplicationEventPublisher events, Clock clock) {
        this.piggies = piggies;
        this.funds = funds;
        this.members = members;
        this.payment = payment;
        this.requests = requests;
        this.events = events;
        this.clock = clock;
    }

    /** Результат: {@code completed} — копилка собрана; {@code pending} — создан запрос на одобрение. */
    public record Result(Piggy piggy, PiggyFunds.Completed completed, Money returned, RequestService.Created pending) {
    }


    public List<Piggy> of(String childId) {
        return piggies.findByChildIdAndStatusOrderByCreatedAtAsc(childId, PiggyStatus.ACTIVE);
    }

    @Transactional
    public Piggy create(String childId, String rawGoal, String rawTarget) {
        Member child = members.self(childId);
        members.requireNotFrozen(child);
        String goal = rawGoal == null ? "" : rawGoal.trim();
        goal = goal.length() > 30 ? goal.substring(0, 30) : goal;
        if (goal.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION, "Напиши, на что копишь");
        }
        Money target = parsePositive(rawTarget);
        List<Piggy> active = of(childId);
        if (active.size() >= MAX_PIGGIES) {
            throw new BusinessException(ErrorCode.PIGGY_MAX, "Можно вести не больше " + MAX_PIGGIES + " копилок");
        }
        String name = goal;
        if (active.stream().anyMatch(p -> p.getGoal().equalsIgnoreCase(name))) {
            throw new BusinessException(ErrorCode.DUPLICATE, "Копилка с таким названием уже есть");
        }
        Piggy piggy = piggies.save(new Piggy(UUID.randomUUID().toString(), child.getFamilyId(), childId, goal, target, Money.ZERO, Instant.now(clock)));
        payment.openSavings(childId, piggy.getId());
        return piggy;
    }

    @Transactional
    public Result deposit(String childId, String piggyId, String rawAmount, String idempotencyKey) {
        Member child = members.self(childId);
        members.requireNotFrozen(child);
        Piggy piggy = piggies.lockActive(piggyId).filter(p -> p.getChildId().equals(childId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Выбери копилку"));
        Money amount = parsePositive(rawAmount);
        Money room = piggy.room();
        if (amount.isGreaterThan(room)) {
            throw new BusinessException(ErrorCode.ROOM, "До цели осталось " + room.format() + " — положи не больше");
        }
        PaymentClient.SavingsResult result = payment.depositToSavings(piggyId, amount, "В копилку «" + piggy.getGoal() + "»", idempotencyKey);
        piggy.recordSaved(result.saved());
        events.publishEvent(new PiggyDeposited(UUID.randomUUID(), Instant.now(clock), child.getFamilyId(), childId, piggyId, amount, DEPOSIT_POINTS));
        PiggyFunds.Completed completed = piggy.isReached() ? funds.complete(child, piggy) : null;
        return new Result(piggy, completed, null, null);
    }

    /** Новая сумма цели. Меньше собранного нельзя; равно собранному — копилка собрана. */
    @Transactional
    public Result updateTarget(String childId, String piggyId, String rawTarget) {
        Member child = members.self(childId);
        members.requireNotFrozen(child);
        Piggy piggy = active(childId, piggyId);
        Money target = parsePositive(rawTarget);
        if (target.isLessThan(piggy.getSaved())) {
            throw new BusinessException(ErrorCode.BELOW_SAVED, "Нельзя меньше уже собранного: " + piggy.getSaved().format());
        }
        if (target.equals(piggy.getTarget())) {
            throw new BusinessException(ErrorCode.UNCHANGED, "Сумма не изменилась");
        }
        if (child.getCohort() == Cohort.JUNIOR) {
            return new Result(piggy, null, null, requests.createPiggyRequest(child, piggyId, piggy.getGoal(), piggy.getSaved(),
                    piggy.getTarget(), RequestKind.PIGGY_UPDATE, target));
        }
        piggy.changeTarget(target);
        PiggyFunds.Completed completed = piggy.isReached() ? funds.complete(child, piggy) : null;
        return new Result(piggy, completed, null, null);
    }

    /** Удаление: накопленное возвращается на счёт. 7–13 — через одобрение. */
    @Transactional
    public Result delete(String childId, String piggyId) {
        Member child = members.self(childId);
        members.requireNotFrozen(child);
        Piggy piggy = active(childId, piggyId);
        if (child.getCohort() == Cohort.JUNIOR) {
            return new Result(piggy, null, null, requests.createPiggyRequest(child, piggyId, piggy.getGoal(), piggy.getSaved(),
                    piggy.getTarget(), RequestKind.PIGGY_DELETE, piggy.getSaved()));
        }
        Money returned = funds.closeAndReturn(piggy);
        return new Result(piggy, null, returned, null);
    }

    private Piggy active(String childId, String piggyId) {
        return piggies.lockActive(piggyId).filter(p -> p.getChildId().equals(childId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Копилка не найдена"));
    }

    private static Money parsePositive(String raw) {
        return Money.parse(raw).filter(Money::isPositive)
                .orElseThrow(() -> new BusinessException(ErrorCode.AMOUNT, "Укажите сумму — не больше двух знаков после запятой"));
    }
}
