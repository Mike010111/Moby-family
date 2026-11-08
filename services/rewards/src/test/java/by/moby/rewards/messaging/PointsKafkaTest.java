package by.moby.rewards.messaging;

import by.moby.common.events.LessonCompleted;
import by.moby.common.events.PaymentCompleted;
import by.moby.common.events.TaskApproved;
import by.moby.common.events.Topics;
import by.moby.common.money.Money;
import by.moby.rewards.points.PointAccountRepository;
import by.moby.rewards.support.KafkaProbe;
import by.moby.rewards.support.KafkaTestcontainersConfig;
import by.moby.rewards.support.RewardsIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static by.moby.rewards.support.TestUsers.misha;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Import(KafkaTestcontainersConfig.class)
@TestPropertySource(properties = {
        "spring.modulith.events.externalization.enabled=true",
        "spring.kafka.admin.auto-create=true",
        "spring.kafka.listener.auto-startup=true"})
class PointsKafkaTest extends RewardsIntegrationTest {

    @Autowired
    KafkaContainer kafka;
    @Autowired
    JsonMapper json;
    @Autowired
    PointAccountRepository points;

    @Test
    @DisplayName("Баллы ребёнка по событиям: оплата +max(1, round), задание +5, урок +10; повтор события — без двойного начисления")
    void pointsAreCreditedOnce() {
        KafkaProbe probe = new KafkaProbe(kafka.getBootstrapServers(), json);
        TaskApproved task = new TaskApproved(UUID.randomUUID(), Instant.now(), "fam-demo", "misha", "t1", "Прочитать", Money.of("2"), 5);
        probe.send(Topics.FAMILY, task);
        probe.send(Topics.FAMILY, task);
        probe.send(Topics.FAMILY, new LessonCompleted(UUID.randomUUID(), Instant.now(), "fam-demo", "misha", 4, 8, 10));
        probe.send(Topics.PAYMENT, new PaymentCompleted(UUID.randomUUID(), Instant.now(), "fam-demo", "misha", "Миша", false, "op-1",
                "Буфет школы №12", Money.of("2.40"), "5462", "cafe", "Кафе и столовые", null));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(points.findById("misha").orElseThrow().getPoints()).isEqualTo(337));
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(points.findById("misha").orElseThrow().getPoints()).isEqualTo(337));
        assertThat(points.findById("mom").orElseThrow().getPoints()).as("баллы родителя не меняются").isEqualTo(3420);
    }

    @Test
    @DisplayName("Покупка артефакта публикует ArtifactPurchased в rewards.events")
    void purchaseIsPublished() throws Exception {
        buy(misha(), "misha", "nut");
        var seen = new KafkaProbe(kafka.getBootstrapServers(), json)
                .await(Topics.REWARDS, r -> "ArtifactPurchased".equals(KafkaProbe.header(r, "eventType")), Duration.ofSeconds(30));
        assertThat(new String(seen.getLast().value())).contains("\"artifactId\":\"nut\"");
    }
}
