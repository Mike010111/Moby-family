package by.moby.payment.account;

/** Технические счета банка — создаются миграцией V2. */
public final class SystemAccounts {

    /** Расчёты с торговыми точками (эквайринг). */
    public static final String MERCHANTS = "sys-merchants";
    /** Клиринг исходящих P2P-переводов. */
    public static final String P2P_CLEARING = "sys-p2p-clearing";
    /** Расходы банка на кэшбэк. */
    public static final String CASHBACK = "sys-cashback";
    /** Входящие остатки при открытии демо-счетов. */
    public static final String OPENING = "sys-opening";

    private SystemAccounts() {
    }
}
