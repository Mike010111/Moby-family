package by.moby.family.members;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Проекция «счёт заблокирован» из событий payment: family проверяет её до обращения к деньгам. */
@Service
public class FrozenProjection {

    private final MemberRepository members;

    public FrozenProjection(MemberRepository members) {
        this.members = members;
    }

    @Transactional
    public void apply(String childId, boolean frozen) {
        members.findByIdAndKind(childId, MemberKind.CHILD).ifPresent(child -> child.setFrozen(frozen));
    }
}
