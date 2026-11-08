package by.moby.family.members;

import by.moby.common.error.BusinessException;
import by.moby.common.error.ErrorCode;
import by.moby.family.platform.security.AccessPolicy;
import by.moby.family.platform.security.CurrentUser;
import org.springframework.stereotype.Component;

/** Поиск участников семьи с проверкой прав. */
@Component
public class Members {

    private final MemberRepository members;
    private final AccessPolicy access;

    public Members(MemberRepository members, AccessPolicy access) {
        this.members = members;
        this.access = access;
    }

    public Member child(String childId) {
        return members.findByIdAndKind(childId, MemberKind.CHILD)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Ребёнок не найден"));
    }

    /** Действие от имени ребёнка — только сам ребёнок. */
    public Member self(String childId) {
        access.requireSelf(childId);
        return child(childId);
    }

    /** Ребёнок, которым управляет родитель этой семьи. */
    public Member managed(String childId) {
        Member child = child(childId);
        access.requireParentOf(child.getFamilyId());
        return child;
    }

    /** Ребёнок, которого видит текущий пользователь: сам или его родитель. */
    public Member readable(String childId) {
        Member child = child(childId);
        access.requireChildOrParent(child.getFamilyId(), childId);
        return child;
    }

    public Member mainRelative(String familyId) {
        return members.findFirstByFamilyIdAndMainTrue(familyId)
                .orElseThrow(() -> new IllegalStateException("Family " + familyId + " has no main relative"));
    }

    /** Родственник ребёнка по id; неизвестный — главный (как в прототипе). */
    public Member relativeOrMain(String familyId, String relativeId) {
        return members.findById(relativeId == null ? "" : relativeId)
                .filter(m -> m.getKind() == MemberKind.RELATIVE && m.getFamilyId().equals(familyId))
                .orElseGet(() -> mainRelative(familyId));
    }

    public CurrentUser currentUser() {
        return access.current();
    }

    public AccessPolicy access() {
        return access;
    }

    /** Счёт ребёнка заблокирован родителем (проекция из payment). */
    public void requireNotFrozen(Member child) {
        if (child.isFrozen()) {
            throw new BusinessException(ErrorCode.FROZEN, "Счёт заблокирован");
        }
    }
}
