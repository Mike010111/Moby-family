package by.moby.family.lessons;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "lesson_progress")
public class LessonProgress {

    @Id
    private String childId;
    private String familyId;
    private int done;
    @Version
    private long version;

    protected LessonProgress() {
    }

    public LessonProgress(String childId, String familyId, int done) {
        this.childId = childId;
        this.familyId = familyId;
        this.done = done;
    }

    boolean allDone() {
        return done >= Lessons.ALL.size();
    }

    void completeNext() {
        if (allDone()) {
            throw new IllegalStateException("All lessons are done");
        }
        done++;
    }

    public String getChildId() {
        return childId;
    }

    public int getDone() {
        return done;
    }
}
