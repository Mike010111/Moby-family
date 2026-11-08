package by.moby.family.support;

import by.moby.common.error.BusinessException;
import by.moby.common.error.ErrorCode;
import by.moby.common.money.Money;
import by.moby.family.payment.PaymentClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Payment в памяти: балансы демо-семьи, копилки, заморозка, идемпотентность по ключу.
 * Повторяет те отказы payment, на которые опирается family. Лимит переводов Ивана — 24,50 BYN.
 */
public class FakePaymentClient implements PaymentClient {

    public record Transfer(String childId, Money amount, TransferKind kind, String title, String key) {
    }

    private final Map<String, Money> children = new HashMap<>();
    private final Map<String, Money> savings = new HashMap<>();
    private final Map<String, String> savingsOwner = new HashMap<>();
    private final Map<String, Money> approvals = new HashMap<>();
    private final Set<String> frozen = new HashSet<>();
    private final Set<String> usedKeys = new HashSet<>();
    private final List<Transfer> transfers = new ArrayList<>();
    private Money parent;

    public FakePaymentClient() {
        reset();
    }

    public synchronized void reset() {
        children.clear();
        savings.clear();
        savingsOwner.clear();
        approvals.clear();
        frozen.clear();
        usedKeys.clear();
        transfers.clear();
        parent = Money.of("842.60");
        children.put("misha", Money.of("12.40"));
        children.put("ivan", Money.of("64.20"));
        piggy("pg-bike", "misha", "86.50");
        piggy("pg-laptop", "ivan", "310");
        piggy("pg-concert", "ivan", "52");
    }

    private void piggy(String id, String owner, String saved) {
        savings.put(id, Money.of(saved));
        savingsOwner.put(id, owner);
    }

    public synchronized Money balance(String childId) {
        return children.get(childId);
    }

    public synchronized Money parentBalance() {
        return parent;
    }

    public synchronized Money saved(String piggyId) {
        return savings.get(piggyId);
    }

    public synchronized Money approval(String childId) {
        return approvals.get(childId);
    }

    public synchronized List<Transfer> transfers() {
        return List.copyOf(transfers);
    }

    public synchronized void freeze(String childId) {
        frozen.add(childId);
    }

    @Override
    public synchronized TransferResult parentToChild(String childId, Money amount, TransferKind kind, String title, String key) {
        if (!usedKeys.add(key)) {
            return new TransferResult(key, amount, children.get(childId), parent);
        }
        if (frozen.contains(childId)) {
            usedKeys.remove(key);
            throw new BusinessException(ErrorCode.FROZEN, "Счёт заблокирован");
        }
        if (amount.isGreaterThan(parent)) {
            usedKeys.remove(key);
            throw new BusinessException(ErrorCode.FUNDS, "Недостаточно средств на счёте");
        }
        parent = parent.minus(amount);
        children.merge(childId, amount, Money::plus);
        transfers.add(new Transfer(childId, amount, kind, title, key));
        return new TransferResult(key, amount, children.get(childId), parent);
    }

    @Override
    public synchronized void grantP2pApproval(String childId, String requestId, Money max) {
        approvals.put(childId, max);
    }

    @Override
    public synchronized void openSavings(String childId, String piggyId) {
        savings.putIfAbsent(piggyId, Money.ZERO);
        savingsOwner.putIfAbsent(piggyId, childId);
    }

    @Override
    public synchronized SavingsResult depositToSavings(String piggyId, Money amount, String title, String key) {
        String owner = savingsOwner.get(piggyId);
        if (frozen.contains(owner)) {
            throw new BusinessException(ErrorCode.FROZEN, "Счёт заблокирован");
        }
        if (amount.isGreaterThan(children.get(owner))) {
            throw new BusinessException(ErrorCode.FUNDS, "Не хватает денег на карте");
        }
        children.merge(owner, amount.negate(), Money::plus);
        savings.merge(piggyId, amount, Money::plus);
        return new SavingsResult(piggyId, amount, savings.get(piggyId), children.get(owner));
    }

    @Override
    public synchronized SavingsResult withdrawSavings(String piggyId, String title, String key) {
        String owner = savingsOwner.get(piggyId);
        Money amount = savings.get(piggyId);
        children.merge(owner, amount, Money::plus);
        savings.put(piggyId, Money.ZERO);
        return new SavingsResult(piggyId, amount, Money.ZERO, children.get(owner));
    }

    @Override
    public synchronized Quote quoteP2p(String childId, String rawAmount, String contactId) {
        String contact = "c-katya".equals(contactId) ? "Катя Соколова" : null;
        Money balance = children.get(childId);
        if (!"ivan".equals(childId)) {
            return new Quote("BLOCKED", false, ErrorCode.AGE, "Переводы доступны с 14 лет", null, null, null, balance, contact);
        }
        Money amount = Money.parse(rawAmount).orElse(null);
        if (amount == null || !amount.isPositive()) {
            return new Quote("BLOCKED", false, ErrorCode.AMOUNT, "Укажите сумму", null, null, null, balance, contact);
        }
        Money remaining = Money.of("24.50");
        if (!amount.isGreaterThan(remaining)) {
            return new Quote("SEND", true, null, null, remaining, "Эта неделя · Переводы", null, balance, contact);
        }
        Money approved = approvals.get(childId);
        if (approved != null && !amount.isGreaterThan(approved)) {
            return new Quote("APPROVED", true, null, null, remaining, "Эта неделя · Переводы", approved, balance, contact);
        }
        return new Quote("REQUEST", false, ErrorCode.NEEDS_APPROVAL, "Сумма выше лимита: доступно " + remaining.format(), remaining,
                "Эта неделя · Переводы", approved, balance, contact);
    }
}
