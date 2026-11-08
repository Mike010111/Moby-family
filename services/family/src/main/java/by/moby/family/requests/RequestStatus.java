package by.moby.family.requests;

import java.util.Map;
import java.util.Set;

/**
 * Машина состояний запроса ребёнка.
 * <pre>
 *   SENT                      — запрос не главному родственнику: «ушёл», в хаб не попадает (конечное)
 *   PENDING ─┬─► APPROVED     — одобрен главным родственником
 *            ├─► DECLINED     — отклонён
 *            └─► CANCELLED    — заменён новым запросом / предмет запроса исчез
 * </pre>
 */
public enum RequestStatus {
    SENT, PENDING, APPROVED, DECLINED, CANCELLED;

    private static final Map<RequestStatus, Set<RequestStatus>> TRANSITIONS = Map.of(
            PENDING, Set.of(APPROVED, DECLINED, CANCELLED));

    public boolean canMoveTo(RequestStatus next) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(next);
    }
}
