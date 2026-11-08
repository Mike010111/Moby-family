package by.moby.payment.p2p;

/** Разовое одобрение перевода сверх лимита: ACTIVE → USED (израсходовано) | REPLACED (новым одобрением). */
public enum ApprovalStatus { ACTIVE, USED, REPLACED }
