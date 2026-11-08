package by.moby.family.overview;

import by.moby.family.members.Geo;
import by.moby.family.members.Member;
import by.moby.family.piggies.Piggy;
import by.moby.family.tasks.Task;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper
interface FamilyViewMapper {

    FamilyView.Relative relative(Member member);

    List<FamilyView.Relative> relatives(List<Member> members);

    @Mapping(target = "updated", source = "updatedAt")
    FamilyView.GeoView geo(Geo geo);

    FamilyView.TaskItem task(Task task);

    List<FamilyView.TaskItem> tasks(List<Task> tasks);

    FamilyView.PiggyItem piggy(Piggy piggy);

    List<FamilyView.PiggyItem> piggies(List<Piggy> piggies);

    @Mapping(target = "id", source = "child.id")
    @Mapping(target = "name", source = "child.name")
    @Mapping(target = "cohort", source = "child.cohort")
    @Mapping(target = "geo", source = "child.geo")
    @Mapping(target = "tasks", source = "tasks")
    @Mapping(target = "piggies", source = "piggies")
    @Mapping(target = "lessons", source = "lessons")
    FamilyView.Child child(Member child, List<Task> tasks, List<Piggy> piggies, FamilyView.LessonsView lessons);
}
