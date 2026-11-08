package by.moby.family.members;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Проекция «счёт заблокирован» из событий payment: family проверяет её до обращения к деньгам. */
@Service
public class FrozenProjection {

    private final MemberRepository members;

    public FrozenProjection(MemberRepository members) {
        this.members = members;
    }

    @Transactional
    public void apply(String childId, boolean frozen, Instant occurredAt) {
        members.findByIdAndKind(childId, MemberKind.CHILD).ifPresent(child -> child.applyFrozen(frozen, occurredAt));
    }
}
