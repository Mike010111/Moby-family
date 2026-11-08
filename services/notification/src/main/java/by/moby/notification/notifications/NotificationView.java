package by.moby.notification.notifications;

import java.time.Instant;

/** Элемент ленты ({@code kind}, {@code title}) или inbox ({@code tone}, {@code seen}) — как в state.feed / child.inbox. */
public record NotificationView(String id, String audience, String childId, String kind, String tone, String title, String text,
                               String requestId, String taskId, Instant ts, boolean seen) {

    public static NotificationView of(Notification n) {
        return new NotificationView(n.getId(), n.getAudience().name(), n.getChildId(), n.getKind(), n.getTone(), n.getTitle(), n.getText(),
                n.getRequestId(), n.getTaskId(), n.getCreatedAt(), n.isSeen());
    }
}
