package by.moby.family.members;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * Участник семьи: родственник (с падежами для подписей «Попросить у мамы») или ребёнок.
 * {@code frozen} у ребёнка — проекция состояния счёта из payment (события AccountFrozen/Unfrozen).
 */
@Entity
@Table(name = "members")
public class Member {

    @Id
    private String id;
    private String familyId;
    @Enumerated(EnumType.STRING)
    private MemberKind kind;
    private String name;
    private String label;
    private String genitive;
    private String dative;
    private String accusative;
    private boolean main;
    private boolean female;
    private int position;

    private String surname;
    private String nameGen;
    private Integer age;
    @Enumerated(EnumType.STRING)
    private Cohort cohort;
    private boolean frozen;
    private Instant frozenChangedAt;
    @Embedded
    private Geo geo;
    private Integer battery;

    @Version
    private long version;

    protected Member() {
    }

    public static Member relative(String id, String familyId, String name, String label, String genitive, String dative,
                                  String accusative, boolean main, boolean female, int position) {
        Member m = new Member();
        m.id = id;
        m.familyId = familyId;
        m.kind = MemberKind.RELATIVE;
        m.name = name;
        m.label = label;
        m.genitive = genitive;
        m.dative = dative;
        m.accusative = accusative;
        m.main = main;
        m.female = female;
        m.position = position;
        return m;
    }

    public static Member child(String id, String familyId, String name, String nameGen, String surname, int age, Cohort cohort,
                               boolean female, Geo geo, int battery, int position) {
        Member m = new Member();
        m.id = id;
        m.familyId = familyId;
        m.kind = MemberKind.CHILD;
        m.name = name;
        m.nameGen = nameGen;
        m.surname = surname;
        m.age = age;
        m.cohort = cohort;
        m.female = female;
        m.geo = geo;
        m.battery = battery;
        m.position = position;
        return m;
    }

    /** Глагол прошедшего времени в роде родственника: «одобрила» / «одобрил». */
    public String verb(String stem) {
        return female ? stem + "а" : stem;
    }

    public void moveTo(Geo geo, int battery) {
        this.geo = geo;
        this.battery = battery;
    }

    /**
     * Применяет событие заморозки, только если оно новее уже применённого: события разных транзакций
     * могут прийти не по порядку (заморозили и сразу разморозили).
     */
    public void applyFrozen(boolean frozen, Instant changedAt) {
        if (frozenChangedAt == null || changedAt.isAfter(frozenChangedAt)) {
            this.frozen = frozen;
            this.frozenChangedAt = changedAt;
        }
    }

    public boolean isChild() {
        return kind == MemberKind.CHILD;
    }

    public String getId() {
        return id;
    }

    public String getFamilyId() {
        return familyId;
    }

    public MemberKind getKind() {
        return kind;
    }

    public String getName() {
        return name;
    }

    public String getLabel() {
        return label;
    }

    public String getGenitive() {
        return genitive;
    }

    public String getDative() {
        return dative;
    }

    public String getAccusative() {
        return accusative;
    }

    public boolean isMain() {
        return main;
    }

    public boolean isFemale() {
        return female;
    }

    public int getPosition() {
        return position;
    }

    public String getSurname() {
        return surname;
    }

    public String getNameGen() {
        return nameGen;
    }

    public Integer getAge() {
        return age;
    }

    public Cohort getCohort() {
        return cohort;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public Geo getGeo() {
        return geo;
    }

    public Integer getBattery() {
        return battery;
    }
}
