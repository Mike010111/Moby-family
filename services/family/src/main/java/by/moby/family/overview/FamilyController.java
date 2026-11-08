package by.moby.family.overview;

import by.moby.family.lessons.LessonService;
import by.moby.family.lessons.Lessons;
import by.moby.family.members.GeoService;
import by.moby.family.members.Member;
import by.moby.family.members.MemberKind;
import by.moby.family.members.MemberRepository;
import by.moby.family.members.Members;
import by.moby.family.piggies.PiggyService;
import by.moby.family.platform.security.CurrentUser;
import by.moby.family.tasks.TaskService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/family")
class FamilyController {

    private final Members members;
    private final MemberRepository memberRepository;
    private final TaskService tasks;
    private final PiggyService piggies;
    private final LessonService lessons;
    private final GeoService geo;
    private final FamilyViewMapper mapper;

    FamilyController(Members members, MemberRepository memberRepository, TaskService tasks, PiggyService piggies,
                     LessonService lessons, GeoService geo, FamilyViewMapper mapper) {
        this.members = members;
        this.memberRepository = memberRepository;
        this.tasks = tasks;
        this.piggies = piggies;
        this.lessons = lessons;
        this.geo = geo;
        this.mapper = mapper;
    }

    @GetMapping("/me")
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

    @PostMapping("/children/{childId}/geo/refresh")
    FamilyView.GeoView refreshGeo(@PathVariable String childId) {
        return mapper.geo(geo.refresh(childId).getGeo());
    }

    private FamilyView.Child child(Member c) {
        return mapper.child(c, tasks.of(c.getId()), piggies.of(c.getId()), new FamilyView.LessonsView(lessons.done(c.getId()), Lessons.ALL.size()));
    }
}
