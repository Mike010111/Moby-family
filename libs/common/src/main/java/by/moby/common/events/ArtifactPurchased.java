package by.moby.common.events;

import java.time.Instant;
import java.util.UUID;

/** Ребёнок купил (или продлил) артефакт маскота за баллы. */
public record ArtifactPurchased(
        UUID eventId,
        Instant occurredAt,
        String familyId,
        String childId,
        String artifactId,
        String artifactName,
        int price,
        int cashbackPercent,
        String category,
        boolean renewal) implements IntegrationEvent {
}
