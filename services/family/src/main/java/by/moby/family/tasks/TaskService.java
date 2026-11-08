package by.moby.family.tasks;

import by.moby.common.error.BusinessException;
import by.moby.common.error.ErrorCode;
import by.moby.common.events.TaskApproved;
import by.moby.common.events.TaskSubmitted;
import by.moby.common.money.Money;
import by.moby.family.members.Member;
import by.moby.family.members.Members;
import by.moby.family.payment.PaymentClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** {@code parent/addTask}, {@code task/complete}, {@code task/approve}. */
@Service
public class TaskService {

    /** Баллы ребёнку за принятое задание. */
    public static final int APPROVAL_POINTS = 5;

    private final TaskRepository tasks;
    private final Members members;
    private final PaymentClient payment;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public TaskService(TaskRepository tasks, Members members, PaymentClient payment, ApplicationEventPublisher events, Clock clock) {
        this.tasks = tasks;
        this.members = members;
        this.payment = payment;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public Task add(String childId, String rawTitle, String rawReward) {
        Member child = members.managed(childId);
        members.requireNotFrozen(child);
        String title = rawTitle == null ? "" : rawTitle.trim();
        title = title.length() > 50 ? title.substring(0, 50) : title;
        if (title.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION, "Опишите задание");
        }
        Money reward = "0".equals(rawReward == null ? null : rawReward.trim())
                ? Money.ZERO
                : Money.parse(rawReward).orElseThrow(() -> new BusinessException(ErrorCode.AMOUNT, "Укажите сумму — не больше двух знаков после запятой"));
        return tasks.save(new Task(UUID.randomUUID().toString(), child.getFamilyId(), childId, title, reward, TaskStatus.OPEN, Instant.now(clock)));
    }

    /** Ребёнок отмечает задание выполненным → на проверку родителю. */
    @Transactional
    public Task submit(String childId, String taskId) {
        Member child = members.self(childId);
        members.requireNotFrozen(child);
        Task task = tasks.lock(taskId, childId).filter(t -> t.canMoveTo(TaskStatus.REVIEW))
                .orElseThrow(() -> new BusinessException(ErrorCode.ALREADY_PROCESSED, "Задание уже отправлено"));
        task.moveTo(TaskStatus.REVIEW);
        events.publishEvent(new TaskSubmitted(UUID.randomUUID(), Instant.now(clock), child.getFamilyId(), childId, child.getName(),
                task.getId(), task.getTitle(), task.getReward()));
        return task;
    }

    /** Родитель принимает задание: награда переводом с его счёта (ключ {@code task:<id>}), +5 баллов ребёнку. */
    @Transactional
    public Task approve(String childId, String taskId) {
        Member child = members.managed(childId);
        members.requireNotFrozen(child);
        Task task = tasks.lock(taskId, childId).filter(t -> t.canMoveTo(TaskStatus.DONE))
                .orElseThrow(() -> new BusinessException(ErrorCode.ALREADY_PROCESSED, "Задание уже проверено"));
        if (task.getReward().isPositive()) {
            payment.parentToChild(childId, task.getReward(), PaymentClient.TransferKind.REWARD, "Награда: " + task.getTitle(), "task:" + task.getId());
        }
        task.moveTo(TaskStatus.DONE);
        events.publishEvent(new TaskApproved(UUID.randomUUID(), Instant.now(clock), child.getFamilyId(), childId, task.getId(),
                task.getTitle(), task.getReward(), APPROVAL_POINTS));
        return task;
    }

    public List<Task> of(String childId) {
        return tasks.findByChildIdOrderByCreatedAtDesc(childId);
    }
}
