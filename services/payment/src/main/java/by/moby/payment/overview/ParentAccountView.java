package by.moby.payment.overview;

import by.moby.common.money.Money;

public record ParentAccountView(String memberId, String name, Money balance, String cardMask, String packageName) {
}
