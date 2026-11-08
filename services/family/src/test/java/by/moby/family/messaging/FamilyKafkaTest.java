package by.moby.family.messaging;

import by.moby.common.events.AccountFrozen;
import by.moby.common.events.Actor;
import by.moby.common.events.Topics;
import by.moby.family.members.MemberRepository;
import by.moby.family.support.FamilyIntegrationTest;
import by.moby.family.support.KafkaProbe;
import by.moby.family.support.KafkaTestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static by.moby.family.support.TestUsers.misha;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Import(KafkaTestcontainersConfig.class)
@TestPropertySource(properties = {
        "spring.modulith.events.externalization.enabled=true",
        "spring.kafka.admin.auto-create=true",
        "spring.kafka.listener.auto-startup=true"})
class FamilyKafkaTest extends FamilyIntegrationTest {

    @Autowired
    KafkaContainer kafka;
    @Autowired
    MemberRepository members;
    @Autowired
    JdbcClient jdbc;

    @Test
    @DisplayName("AccountFrozen из payment обновляет проекцию; повторная доставка обрабатывается один раз")
    void frozenProjectionIsIdempotent() {
        AccountFrozen event = new AccountFrozen(UUID.randomUUID(), Instant.now(), "fam-demo", "misha", "Миша", "Карта учащегося", "•• 7890",
                new Actor("mom", "Мама", true));
        KafkaProbe probe = new KafkaProbe(kafka.getBootstrapServers(), json);
        probe.send(Topics.PAYMENT, event);
        probe.send(Topics.PAYMENT, event);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(members.findById("misha").orElseThrow().isFrozen()).isTrue());
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(jdbc.sql("select count(*) from processed_events where event_id = :id").param("id", event.eventId())
                        .query(Long.class).single()).isEqualTo(1));
    }

    @Test
    @DisplayName("RequestCreated уходит в family.events через outbox")
    void requestCreatedIsPublished() throws Exception {
        String id = body(postAs(misha(), "/api/family/requests", "{\"childId\":\"misha\",\"relativeId\":\"mom\",\"amount\":\"5\",\"reason\":\"На обед\"}"))
                .at("/request/id").asString();
        var seen = new KafkaProbe(kafka.getBootstrapServers(), json)
                .await(Topics.FAMILY, r -> json.readTree(r.value()).path("requestId").asString("").equals(id), Duration.ofSeconds(30));
        assertThat(KafkaProbe.header(seen.getLast(), "eventType")).isEqualTo("RequestCreated");
    }
}
