package by.moby.family.lessons;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class LessonController {

    record LessonsView(int done, int total, int points) {
    }

    private final LessonService lessons;

    LessonController(LessonService lessons) {
        this.lessons = lessons;
    }

    @PostMapping("/api/family/children/{childId}/lessons/complete")
    LessonsView complete(@PathVariable String childId) {
        return new LessonsView(lessons.completeNext(childId).getDone(), Lessons.ALL.size(), Lessons.POINTS);
    }
}
