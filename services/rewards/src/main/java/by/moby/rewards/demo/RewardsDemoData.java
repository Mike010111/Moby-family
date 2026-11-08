package by.moby.rewards.demo;

import by.moby.rewards.artifacts.ArtifactCatalog;
import by.moby.rewards.artifacts.ArtifactState;
import by.moby.rewards.artifacts.ArtifactStateRepository;
import by.moby.rewards.artifacts.Cohort;
import by.moby.rewards.artifacts.OwnedArtifact;
import by.moby.rewards.artifacts.OwnedArtifactRepository;
import by.moby.rewards.artifacts.Slot;
import by.moby.rewards.points.PointAccount;
import by.moby.rewards.points.PointAccountRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;

/**
 * Баллы и маскоты из initial-state.js ({@code artifactsState}): текущая неделя цикла — «в руках»,
 * у Миши — синие очки (куплены 2 недели назад), у Ивана — тёмные очки и коричневый жилет.
 */
@Component
@Profile("demo")
public class RewardsDemoData {

    public static final String FAMILY = "fam-demo";

    private final PointAccountRepository points;
    private final ArtifactStateRepository states;
    private final OwnedArtifactRepository owned;
    private final JdbcClient jdbc;
    private final Clock clock;

    public RewardsDemoData(PointAccountRepository points, ArtifactStateRepository states, OwnedArtifactRepository owned,
                           JdbcClient jdbc, Clock clock) {
        this.points = points;
        this.states = states;
        this.owned = owned;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public void seedIfEmpty() {
        if (points.count() == 0) {
            seed();
        }
    }

    @Transactional
    public void reset() {
        jdbc.sql("truncate point_entries, point_accounts, owned_artifacts, artifact_sold_out, artifact_states, processed_events").update();
        seed();
    }

    private void seed() {
        points.saveAll(List.of(new PointAccount("mom", FAMILY, 3420), new PointAccount("misha", FAMILY, 320), new PointAccount("ivan", FAMILY, 860)));
        Instant weekStart = LocalDate.now(clock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(clock.getZone()).toInstant();
        child("misha", Cohort.JUNIOR, weekStart, Map.of("glasses-blue", 2));
        child("ivan", Cohort.TEEN, weekStart, Map.of("glasses-dark", 2, "vest-brown", 1));
    }

    private void child(String childId, Cohort cohort, Instant weekStart, Map<String, Integer> boughtWeeksAgo) {
        ArtifactState state = states.save(new ArtifactState(childId, FAMILY, cohort, Slot.HANDS, weekStart));
        boughtWeeksAgo.forEach((id, weeksAgo) -> {
            ArtifactCatalog.Artifact artifact = ArtifactCatalog.byId(id).orElseThrow();
            owned.save(new OwnedArtifact(childId, artifact, cohort, weekStart.minus(ArtifactState.WEEK.multipliedBy(weeksAgo)),
                    state.nextSlotWeekStart(artifact.slot())));
        });
    }
}
