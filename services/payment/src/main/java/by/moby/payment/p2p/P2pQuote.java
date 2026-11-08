package by.moby.payment.p2p;

import by.moby.common.error.ErrorCode;
import by.moby.common.money.Money;

/**
 * Котировка перевода ({@code quoteP2P}): что произойдёт при нажатии кнопки.
 * <ul>
 *   <li>{@code SEND} — укладывается в лимиты;</li>
 *   <li>{@code APPROVED} — сверх лимита, но в пределах одобрения родителя (одобрение будет израсходовано);</li>
 *   <li>{@code REQUEST} — сверх лимита: нужно просить родителя (код NEEDS_APPROVAL);</li>
 *   <li>{@code BLOCKED} — перевод невозможен (код и причина).</li>
 * </ul>
 * {@code contactName} — имя получателя, если в запросе передан известный {@code contactId}.
 */
public record P2pQuote(Mode mode, boolean ok, ErrorCode code, String reason, Money remaining, String limitName,
                       Money approvalMax, Money balance, String contactName) {

    public enum Mode { SEND, APPROVED, REQUEST, BLOCKED }

    static P2pQuote blocked(ErrorCode code, String reason, Money balance) {
        return new P2pQuote(Mode.BLOCKED, false, code, reason, null, null, null, balance, null);
    }

    P2pQuote withContact(String name) {
        return new P2pQuote(mode, ok, code, reason, remaining, limitName, approvalMax, balance, name);
    }
}
