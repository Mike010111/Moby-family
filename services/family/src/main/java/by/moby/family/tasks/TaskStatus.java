package by.moby.family.tasks;

/**
 * Машина состояний задания: OPEN ─(ребёнок: выполнено)─► REVIEW ─(родитель: принято)─► DONE.
 */
public enum TaskStatus {
    OPEN, REVIEW, DONE;

    public boolean canMoveTo(TaskStatus next) {
        return (this == OPEN && next == REVIEW) || (this == REVIEW && next == DONE);
    }
}
