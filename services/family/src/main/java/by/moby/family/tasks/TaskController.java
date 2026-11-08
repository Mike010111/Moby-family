package by.moby.family.tasks;

import by.moby.common.money.Money;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/family/children/{childId}/tasks")
class TaskController {

    record NewTask(String title, String reward) {
    }

    record TaskView(String id, String title, Money reward, String status) {
        static TaskView of(Task t) {
            return new TaskView(t.getId(), t.getTitle(), t.getReward(), t.getStatus().name());
        }
    }

    private final TaskService tasks;

    TaskController(TaskService tasks) {
        this.tasks = tasks;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TaskView add(@PathVariable String childId, @RequestBody NewTask body) {
        return TaskView.of(tasks.add(childId, body.title(), body.reward()));
    }

    @PostMapping("/{taskId}/submit")
    TaskView submit(@PathVariable String childId, @PathVariable String taskId) {
        return TaskView.of(tasks.submit(childId, taskId));
    }

    @PostMapping("/{taskId}/approve")
    TaskView approve(@PathVariable String childId, @PathVariable String taskId) {
        return TaskView.of(tasks.approve(childId, taskId));
    }
}
