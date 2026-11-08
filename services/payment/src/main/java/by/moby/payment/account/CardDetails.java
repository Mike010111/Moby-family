package by.moby.payment.account;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Реквизиты карты учащегося (в демо — витринные данные). */
@Embeddable
public class CardDetails {

    @Column(name = "card_title")
    private String title;
    @Column(name = "card_mask")
    private String mask;
    @Column(name = "card_number")
    private String number;
    @Column(name = "card_valid_thru")
    private String validThru;
    @Column(name = "card_student_id")
    private String studentId;
    @Column(name = "card_holder")
    private String holder;
    @Column(name = "card_image")
    private String image;

    protected CardDetails() {
    }

    public CardDetails(String title, String mask, String number, String validThru, String studentId, String holder, String image) {
        this.title = title;
        this.mask = mask;
        this.number = number;
        this.validThru = validThru;
        this.studentId = studentId;
        this.holder = holder;
        this.image = image;
    }

    public String getTitle() {
        return title;
    }

    public String getMask() {
        return mask;
    }

    public String getNumber() {
        return number;
    }

    public String getValidThru() {
        return validThru;
    }

    public String getStudentId() {
        return studentId;
    }

    public String getHolder() {
        return holder;
    }

    public String getImage() {
        return image;
    }
}
