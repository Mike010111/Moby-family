package by.moby.family.overview;

import by.moby.family.lessons.LessonService;
import by.moby.family.lessons.Lessons;
import by.moby.family.members.Member;
import by.moby.family.members.MemberKind;
import by.moby.family.members.MemberRepository;
import by.moby.family.members.Members;
import by.moby.family.piggies.PiggyService;
import by.moby.family.platform.security.CurrentUser;
import by.moby.family.tasks.TaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Семья глазами текущего пользователя (read-модель для экранов). */
@Service
class FamilyQueryService {

    private final Members members;
    private final MemberRepository memberRepository;
    private final TaskService tasks;
    private final PiggyService piggies;
    private final LessonService lessons;
    private final FamilyViewMapper mapper;

    FamilyQueryService(Members members, MemberRepository memberRepository, TaskService tasks, PiggyService piggies,
                       LessonService lessons, FamilyViewMapper mapper) {
        this.members = members;
        this.memberRepository = memberRepository;
        this.tasks = tasks;
        this.piggies = piggies;
        this.lessons = lessons;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    FamilyView me() {
        CurrentUser user = members.currentUser();
        List<Member> family = memberRepository.findByFamilyIdOrderByPositionAsc(user.familyId());
        List<Member> relatives = family.stream().filter(m -> m.getKind() == MemberKind.RELATIVE).toList();
        List<FamilyView.Child> children = family.stream()
                .filter(Member::isChild)
                .filter(c -> user.isParent() || c.getId().equals(user.memberId()))
                .map(this::child)
                .toList();
        return new FamilyView(user.familyId(), new FamilyView.Me(user.memberId(), user.role().name()), mapper.relatives(relatives), children);
    }

    private FamilyView.Child child(Member c) {
        return mapper.child(c, tasks.of(c.getId()), piggies.of(c.getId()), new FamilyView.LessonsView(lessons.done(c.getId()), Lessons.ALL.size()));
    }
}
