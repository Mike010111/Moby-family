package by.moby.notification.platform.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Правила доступа: родитель — только к детям своей семьи, ребёнок — только к себе.
 * Проверка локальная: {@code familyId} хранится у каждой записи сервиса.
 */
@Component
public class AccessPolicy {

    private final CurrentUserProvider users;

    public AccessPolicy(CurrentUserProvider users) {
        this.users = users;
    }

    /** Читать и действовать от имени ребёнка: сам ребёнок или родитель его семьи. */
    public CurrentUser requireChildOrParent(String childFamilyId, String childId) {
        CurrentUser user = users.get();
        boolean self = !user.isParent() && user.memberId().equals(childId);
        boolean parent = user.isParent() && user.familyId().equals(childFamilyId);
        if (!self && !parent) {
            throw new AccessDeniedException("No access to child " + childId);
        }
        return user;
    }

    /** Только родитель этой семьи. */
    public CurrentUser requireParentOf(String familyId) {
        CurrentUser user = users.get();
        if (!user.isParent() || !user.familyId().equals(familyId)) {
            throw new AccessDeniedException("Parent of family " + familyId + " required");
        }
        return user;
    }

    /** Только сам ребёнок. */
    public CurrentUser requireSelf(String childId) {
        CurrentUser user = users.get();
        if (user.isParent() || !user.memberId().equals(childId)) {
            throw new AccessDeniedException("Only the child can do this");
        }
        return user;
    }

    public CurrentUser current() {
        return users.get();
    }
}
