package by.moby.payment.limits.api;

import java.util.List;

/** Сумма — строкой, как в поле ввода: «5,25» допустимо, «1.234» — нет (код AMOUNT). */
public record LimitRequest(Integer count, String unit, String amount, List<String> categories) {
}
