package by.moby.payment.catalog;

/** Возрастная когорта ребёнка (COHORTS в rules.js). */
public enum Cohort {
    JUNIOR("7–13 лет", false),
    TEEN("14–17 лет", true);

    private final String label;
    private final boolean p2pAllowed;

    Cohort(String label, boolean p2pAllowed) {
        this.label = label;
        this.p2pAllowed = p2pAllowed;
    }

    public String label() {
        return label;
    }

    public boolean p2pAllowed() {
        return p2pAllowed;
    }
}
