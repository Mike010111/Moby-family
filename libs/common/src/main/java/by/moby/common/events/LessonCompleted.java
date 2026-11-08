package by.moby.common.events;

import java.time.Instant;
import java.util.UUID;

/** Пройден урок финансовой грамотности: начисляются {@code points} баллов. */
public record LessonCompleted(
        UUID eventId,
        Instant occurredAt,
        String familyId,
        String childId,
        int lessonsDone,
        int lessonsTotal,
        int points) implements IntegrationEvent {
}
