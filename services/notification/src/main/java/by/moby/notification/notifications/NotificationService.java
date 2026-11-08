package by.moby.notification.notifications;

import by.moby.common.error.BusinessException;
import by.moby.common.error.ErrorCode;
import by.moby.notification.platform.security.AccessPolicy;
import by.moby.notification.platform.security.CurrentUser;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;

@Service
public class NotificationService {

    /** Сколько хранит экран: лента — 40 событий, inbox — 20 (как в прототипе). */
    static final int FEED_SIZE = 40;
    static final int INBOX_SIZE = 20;

    private final NotificationRepository notifications;
    private final AccessPolicy access;
    private final SseHub hub;

    public NotificationService(NotificationRepository notifications, AccessPolicy access, SseHub hub) {
        this.notifications = notifications;
        this.access = access;
        this.hub = hub;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Notification feed(String familyId, String childId, NotificationTexts.Feed text, Instant at, String requestId, String taskId) {
        Notification n = notifications.save(Notification.feed(familyId, childId, text.kind(), text.title(), text.text(), at)
                .withRequest(requestId).withTask(taskId));
        pushAfterCommit(n);
        return n;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Notification inbox(String familyId, String childId, NotificationTexts.Inbox text, Instant at) {
        Notification n = notifications.save(Notification.inbox(familyId, childId, text.text(), text.tone(), at));
        pushAfterCommit(n);
        return n;
    }

    /** Сигнал «данные ребёнка изменились» без записи в ленту: экран перечитает балансы. */
    public void pingAfterCommit(String familyId, String childId, String what) {
        afterCommit(() -> hub.changed(familyId, childId, what));
    }

    @Transactional(readOnly = true)
    public List<Notification> feedOf() {
        CurrentUser user = access.current();
        access.requireParentOf(user.familyId());
        return notifications.findByFamilyIdAndAudienceOrderByCreatedAtDesc(user.familyId(), Audience.FEED, Limit.of(FEED_SIZE));
    }

    /** Inbox ребёнка: сам ребёнок или родитель (выборка всегда ограничена семьёй из токена). */
    @Transactional(readOnly = true)
    public List<Notification> inboxOf(String childId) {
        CurrentUser user = access.current();
        if (!user.isParent() && !user.memberId().equals(childId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Нет доступа");
        }
        return notifications.findByFamilyIdAndAudienceAndChildIdOrderByCreatedAtDesc(user.familyId(), Audience.INBOX, childId, Limit.of(INBOX_SIZE));
    }

    /** {@code ui/markSeen}: ребёнок открыл свой экран — уведомления показаны. */
    @Transactional
    public int markSeen(String childId) {
        CurrentUser user = access.requireSelf(childId);
        List<Notification> unseen = notifications.findByFamilyIdAndAudienceAndChildIdAndSeenFalse(user.familyId(), Audience.INBOX, childId);
        unseen.forEach(Notification::markSeen);
        return unseen.size();
    }

    private void pushAfterCommit(Notification n) {
        afterCommit(() -> hub.notification(n));
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
