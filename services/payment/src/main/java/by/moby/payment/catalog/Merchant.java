package by.moby.payment.catalog;

import by.moby.common.money.Money;

/** Торговая точка с QR-кодом КРОК (демо-справочник). */
public record Merchant(String id, String name, String item, Money amount, String mcc) {
}
