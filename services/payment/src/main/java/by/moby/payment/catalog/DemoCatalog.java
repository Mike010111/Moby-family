package by.moby.payment.catalog;

import by.moby.common.money.Money;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** QR-коды и контакты из catalog.js. В продуктиве пришли бы из процессинга / адресной книги. */
public final class DemoCatalog {

    public static final Map<Cohort, List<Merchant>> MERCHANTS = Map.of(
            Cohort.JUNIOR, List.of(
                    m("m-buffet", "Буфет школы №12", "Булочка и сок", "2.40", "5462"),
                    m("m-office", "Канцтовары «Скрепка»", "Тетради, 3 шт.", "3.80", "5943"),
                    m("m-burger", "Бургерная у метро", "Комбо-обед", "7.90", "5814"),
                    m("m-slots", "Зал игровых автоматов", "Жетоны", "5.00", "7995")),
            Cohort.TEEN, List.of(
                    m("t-coffee", "Кофейня «Зерно»", "Раф и круассан", "8.60", "5812"),
                    m("t-cinema", "Кинотеатр «Октябрь»", "Билет, 19:30", "12.00", "7832"),
                    m("t-books", "Книжный «Академкнига»", "Учебник по физике", "24.50", "5942"),
                    m("t-bet", "Букмекерская контора", "Ставка", "10.00", "7995")));

    public static final List<Contact> CONTACTS = List.of(
            new Contact("c-katya", "Катя Соколова", "+375 29 ••• 41 12", "КС"),
            new Contact("c-danya", "Даня Миронов", "+375 33 ••• 08 77", "ДМ"),
            new Contact("c-tima", "Тимур Ахмедов", "+375 44 ••• 63 05", "ТА"));

    private DemoCatalog() {
    }

    private static Merchant m(String id, String name, String item, String amount, String mcc) {
        return new Merchant(id, name, item, Money.of(amount), mcc);
    }

    public static Optional<Merchant> merchant(Cohort cohort, String id) {
        return MERCHANTS.get(cohort).stream().filter(m -> m.id().equals(id)).findFirst();
    }

    public static Optional<Contact> contact(String id) {
        return CONTACTS.stream().filter(c -> c.id().equals(id)).findFirst();
    }
}
