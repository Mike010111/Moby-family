package by.moby.family.lessons;

import by.moby.common.error.BusinessException;
import by.moby.common.error.ErrorCode;
import by.moby.common.events.LessonCompleted;
import by.moby.family.members.Member;
import by.moby.family.members.Members;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** {@code lesson/complete}: работает и при заблокированном счёте — баллы не блокируются. */
@Service
public class LessonService {

    private final LessonProgressRepository progress;
    private final Members members;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public LessonService(LessonProgressRepository progress, Members members, ApplicationEventPublisher events, Clock clock) {
        this.progress = progress;
        this.members = members;
        this.events = events;
        this.clock = clock;
    }

    public int done(String childId) {
        return progress.findById(childId).map(LessonProgress::getDone).orElse(0);
    }

    @Transactional
    public LessonProgress completeNext(String childId) {
        Member child = members.self(childId);
        LessonProgress p = progress.findById(childId).orElseGet(() -> progress.save(new LessonProgress(childId, child.getFamilyId(), 0)));
        if (p.allDone()) {
            throw new BusinessException(ErrorCode.LESSONS_DONE, "Все уроки пройдены");
        }
        p.completeNext();
        events.publishEvent(new LessonCompleted(UUID.randomUUID(), Instant.now(clock), child.getFamilyId(), childId, p.getDone(),
                Lessons.ALL.size(), Lessons.POINTS));
        return p;
    }
}
