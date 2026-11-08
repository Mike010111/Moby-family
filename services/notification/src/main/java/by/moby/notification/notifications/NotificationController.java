package by.moby.notification.notifications;

import by.moby.notification.platform.security.AccessPolicy;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
class NotificationController {

    record SeenRequest(String childId) {
    }

    record SeenView(int marked) {
    }

    private final NotificationService notifications;
    private final SseHub hub;
    private final AccessPolicy access;

    NotificationController(NotificationService notifications, SseHub hub, AccessPolicy access) {
        this.notifications = notifications;
        this.hub = hub;
        this.access = access;
    }

    @GetMapping("/feed")
    List<NotificationView> feed() {
        return notifications.feedOf().stream().map(NotificationView::of).toList();
    }

    @GetMapping("/inbox")
    List<NotificationView> inbox(@RequestParam String childId) {
        return notifications.inboxOf(childId).stream().map(NotificationView::of).toList();
    }

    @PostMapping("/inbox/seen")
    SeenView seen(@RequestBody SeenRequest request) {
        return new SeenView(notifications.markSeen(request.childId()));
    }

    /**
     * Поток уведомлений текущего пользователя: {@code notification} — новый элемент ленты / inbox,
     * {@code changed} — данные ребёнка изменились (перечитать балансы).
     */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream() {
        return hub.subscribe(access.current());
    }
}
