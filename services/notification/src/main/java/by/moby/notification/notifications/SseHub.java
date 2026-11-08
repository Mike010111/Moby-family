package by.moby.notification.notifications;

import by.moby.notification.platform.security.CurrentUser;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Подписчики SSE этого инстанса. Родитель получает ленту семьи и сигналы по всем детям,
 * ребёнок — только своё. Подписки живут в памяти инстанса (см. ADR-004 о масштабировании).
 */
@Component
public class SseHub {

    private static final Logger log = LoggerFactory.getLogger(SseHub.class);
    private static final Duration TIMEOUT = Duration.ofMinutes(30);

    private record Subscriber(CurrentUser user, SseEmitter emitter) {

        boolean sees(String familyId, String childId) {
            return user.familyId().equals(familyId) && (user.isParent() || user.memberId().equals(childId));
        }
    }

    private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();

    public SseHub(MeterRegistry meters) {
        meters.gaugeCollectionSize("moby.sse.subscribers", List.of(), subscribers);
    }

    public SseEmitter subscribe(CurrentUser user) {
        SseEmitter emitter = new SseEmitter(TIMEOUT.toMillis());
        Subscriber subscriber = new Subscriber(user, emitter);
        subscribers.add(subscriber);
        emitter.onCompletion(() -> subscribers.remove(subscriber));
        emitter.onTimeout(() -> subscribers.remove(subscriber));
        emitter.onError(e -> subscribers.remove(subscriber));
        send(subscriber, SseEmitter.event().name("ready").data(Map.of("memberId", user.memberId())));
        return emitter;
    }

    /** Лента — родителю семьи; inbox — ребёнку-адресату (и родителю, чтобы обновить его экран). */
    public void notification(Notification n) {
        Predicate<Subscriber> audience = n.getAudience() == Audience.FEED
                ? s -> s.user().isParent() && s.user().familyId().equals(n.getFamilyId())
                : s -> s.sees(n.getFamilyId(), n.getChildId());
        NotificationView view = NotificationView.of(n);
        broadcast(audience, () -> SseEmitter.event().name("notification").id(n.getId()).data(view));
    }

    public void changed(String familyId, String childId, String what) {
        broadcast(s -> s.sees(familyId, childId), () -> SseEmitter.event().name("changed").data(Map.of("childId", childId, "event", what)));
    }

    /** Комментарий-«пульс»: прокси и браузер не закрывают простаивающее соединение. */
    @Scheduled(fixedRate = 25_000)
    void heartbeat() {
        broadcast(s -> true, () -> SseEmitter.event().comment("keep-alive"));
    }

    /** Событие собирается заново для каждого подписчика: {@code SseEventBuilder} одноразовый. */
    private void broadcast(Predicate<Subscriber> who, Supplier<SseEmitter.SseEventBuilder> event) {
        subscribers.stream().filter(who).forEach(s -> send(s, event.get()));
    }

    private void send(Subscriber s, SseEmitter.SseEventBuilder event) {
        try {
            s.emitter().send(event);
        } catch (IOException | IllegalStateException e) {
            log.debug("SSE subscriber {} gone: {}", s.user().memberId(), e.getMessage());
            subscribers.remove(s);
        }
    }
}
