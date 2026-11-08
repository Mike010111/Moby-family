package by.moby.payment.catalog;

import java.util.List;

/**
 * Категория трат (CATEGORIES / HARD_BLOCKS в rules.js).
 *
 * @param channel   true — канал оплаты (интернет), а не тип торговой точки
 * @param minCohort когорта, начиная с которой категория доступна (null — всем)
 */
public record Category(String key, String label, String icon, List<String> mcc, boolean channel, Cohort minCohort) {
}
