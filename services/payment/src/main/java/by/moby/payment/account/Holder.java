package by.moby.payment.account;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Подписи владельца счёта для текстов операций и событий
 * («Пополнение от мамы», «одобрено маме», «Мама заблокировала счёт»).
 */
@Embeddable
public class Holder {

    @Column(name = "holder_name")
    private String name;
    @Column(name = "holder_label")
    private String label;
    @Column(name = "holder_genitive")
    private String genitive;
    @Column(name = "holder_dative")
    private String dative;
    @Column(name = "holder_female")
    private boolean female;

    protected Holder() {
    }

    public Holder(String name, String label, String genitive, String dative, boolean female) {
        this.name = name;
        this.label = label;
        this.genitive = genitive;
        this.dative = dative;
        this.female = female;
    }

    public String name() {
        return name;
    }

    public String label() {
        return label;
    }

    public String genitive() {
        return genitive;
    }

    public String dative() {
        return dative;
    }

    public boolean female() {
        return female;
    }
}
