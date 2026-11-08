package by.moby.rewards.points;

import by.moby.common.error.BusinessException;
import by.moby.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;

@Service
public class PointsService {

    private final PointAccountRepository accounts;
    private final PointEntryRepository entries;
    private final Clock clock;

    public PointsService(PointAccountRepository accounts, PointEntryRepository entries, Clock clock) {
        this.accounts = accounts;
        this.entries = entries;
        this.clock = clock;
    }

    /** Баллы за покупку: {@code Math.max(1, Math.round(amount))} из прототипа. */
    public static int forPurchase(BigDecimal amount) {
        return Math.max(1, amount.setScale(0, RoundingMode.HALF_UP).intValueExact());
    }

    public PointAccount account(String memberId) {
        return accounts.findById(memberId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Баллы не найдены"));
    }

    /** {@code SELECT … FOR UPDATE} по счёту баллов: сериализует покупки одного ребёнка. */
    @Transactional(propagation = Propagation.MANDATORY)
    public PointAccount lock(String memberId) {
        return accounts.lock(memberId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Баллы не найдены"));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public PointAccount credit(String memberId, int points, String reason) {
        PointAccount account = accounts.lock(memberId).orElseThrow(() -> new IllegalStateException("No points account for " + memberId));
        account.add(points);
        entries.save(new PointEntry(memberId, points, reason, Instant.now(clock)));
        return account;
    }

    /** Списание с блокировкой строки: параллельные покупки не уводят баллы в минус. */
    @Transactional(propagation = Propagation.MANDATORY)
    public PointAccount debit(String memberId, int points, String reason) {
        PointAccount account = accounts.lock(memberId).orElseThrow(() -> new IllegalStateException("No points account for " + memberId));
        if (!account.has(points)) {
            throw new BusinessException(ErrorCode.POINTS, "Не хватает " + (points - account.getPoints()) + " баллов");
        }
        account.add(-points);
        entries.save(new PointEntry(memberId, -points, reason, Instant.now(clock)));
        return account;
    }
}
