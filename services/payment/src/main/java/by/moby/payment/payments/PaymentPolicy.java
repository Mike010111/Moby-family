package by.moby.payment.payments;

import by.moby.common.error.BusinessException;
import by.moby.common.error.ErrorCode;
import by.moby.common.money.Money;
import by.moby.payment.account.Account;
import by.moby.payment.catalog.Categories;
import by.moby.payment.limits.LimitMath;

import java.util.Optional;
import java.util.function.Function;

/**
 * Проверка покупки — порт {@code checkPayment}. Порядок проверок = порядок причин отказа:
 * HARD_BLOCK → FROZEN → PARENT_CATEGORY → PARENT_ONLINE → FUNDS → LIMIT.
 */
public final class PaymentPolicy {

    private PaymentPolicy() {
    }

    /**
     * @param tightest самый жёсткий лимит для операции (считается лениво — только если дошли до проверки)
     * @param limitName подпись лимита для текста отказа
     */
    public static Optional<BusinessException> check(Account child, LimitMath.Spend op,
                                                    java.util.function.Supplier<Optional<LimitMath.Tight>> tightest,
                                                    Function<LimitMath.Tight, String> limitName) {
        String group = Categories.groupOf(op.mcc());
        Optional<BusinessException> hard = Categories.hardBlock(group)
                .map(h -> new BusinessException(ErrorCode.HARD_BLOCK, "«" + h.label() + "» — запрещено для несовершеннолетних"));
        if (hard.isPresent()) {
            return hard;
        }
        if (child.isFrozen()) {
            return deny(ErrorCode.FROZEN, "Счёт заблокирован");
        }
        if (child.isDenied(group)) {
            return deny(ErrorCode.PARENT_CATEGORY, "Категория «" + Categories.label(group) + "» запрещена");
        }
        if (op.online() && !child.isAllowed(Categories.ONLINE)) {
            return deny(ErrorCode.PARENT_ONLINE, "Покупки в интернете запрещены");
        }
        if (!child.hasFunds(op.amount())) {
            return deny(ErrorCode.FUNDS, "Недостаточно средств");
        }
        Optional<LimitMath.Tight> tight = tightest.get();
        if (tight.isPresent() && op.amount().isGreaterThan(tight.get().usage().remaining())) {
            Money remaining = tight.get().usage().remaining();
            return deny(ErrorCode.LIMIT, "Превышен лимит «" + limitName.apply(tight.get()) + "»: осталось " + remaining.format());
        }
        return Optional.empty();
    }

    private static Optional<BusinessException> deny(ErrorCode code, String reason) {
        return Optional.of(new BusinessException(code, reason));
    }
}
