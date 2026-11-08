package by.moby.payment.limits.api;

import by.moby.common.money.Money;

import java.time.Instant;
import java.util.List;

public record LimitView(
        String id,
        int count,
        String unit,
        Money amount,
        List<String> categories,
        Instant start,
        String title,
        String duration,
        String categoriesLabel,
        Usage usage) {

    public record Usage(Money spent, Money remaining, boolean exhausted, Money overBy, int percent) {
    }
}
