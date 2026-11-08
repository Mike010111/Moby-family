package by.moby.family.scenarios;

import by.moby.family.members.FrozenProjection;
import by.moby.family.members.MemberRepository;
import by.moby.family.support.FamilyIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class FrozenProjectionOrderTest extends FamilyIntegrationTest {

    @Autowired
    FrozenProjection projection;
    @Autowired
    MemberRepository members;

    @Test
    @DisplayName("Запоздавшее событие заморозки не перетирает более позднюю разморозку")
    void staleEventIsIgnored() {
        Instant frozenAt = Instant.parse("2026-10-21T09:00:00Z");
        projection.apply("misha", false, frozenAt.plus(Duration.ofSeconds(1)));
        projection.apply("misha", true, frozenAt);
        assertThat(members.findById("misha").orElseThrow().isFrozen()).isFalse();
    }
}
