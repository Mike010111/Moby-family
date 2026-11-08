package by.moby.payment.account;

public enum AccountKind {
    /** Счёт главного родственника — источник пополнений. */
    PARENT,
    /** Карта учащегося. */
    CHILD,
    /** Деньги копилки ребёнка ({@code externalRef} = id копилки в family). */
    SAVINGS,
    /** Технические счета банка: эквайринг, клиринг P2P, кэшбэк, входящие остатки. */
    SYSTEM
}
