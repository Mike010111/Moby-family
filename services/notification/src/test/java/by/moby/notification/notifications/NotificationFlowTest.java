package by.moby.notification.notifications;

import by.moby.common.events.Actor;
import by.moby.common.events.RequestCreated;
import by.moby.common.events.RequestKind;
import by.moby.common.events.RequestResolved;
import by.moby.common.events.Topics;
import by.moby.common.money.Money;
import by.moby.notification.demo.NotificationDemoData;
import by.moby.notification.support.KafkaProbe;
import by.moby.notification.support.KafkaTestcontainersConfig;
import by.moby.notification.support.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static by.moby.notification.support.TestUsers.anna;
import static by.moby.notification.support.TestUsers.ivan;
import static by.moby.notification.support.TestUsers.misha;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** События из Kafka → лента родителя / inbox ребёнка → REST и SSE. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, KafkaTestcontainersConfig.class})
@ActiveProfiles({"test", "demo"})
@TestPropertySource(properties = {"spring.kafka.listener.auto-startup=true", "spring.kafka.admin.auto-create=true"})
class NotificationFlowTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    KafkaContainer kafka;
    @Autowired
    JsonMapper json;
    @Autowired
    NotificationDemoData demo;
    @Autowired
    NotificationRepository notifications;

    KafkaProbe probe;

    @BeforeEach
    void setUp() {
        demo.reset();
        probe = new KafkaProbe(kafka.getBootstrapServers(), json);
    }

    @Test
    @DisplayName("Запрос «5 BYN на обед» попадает в ленту родителя один раз; одобрение — в inbox ребёнка")
    void requestFlow() throws Exception {
        mvc.perform(get("/api/notifications/feed").with(anna())).andExpect(jsonPath("$", hasSize(1)));
        RequestCreated created = new RequestCreated(UUID.randomUUID(), Instant.now(), "fam-demo", "misha", "Миша", "req-1", RequestKind.TOPUP,
                "mom", true, Money.of("5"), "На обед", null, null, null, null, null);
        probe.send(Topics.FAMILY, created);
        probe.send(Topics.FAMILY, created);
        probe.send(Topics.FAMILY, new RequestCreated(UUID.randomUUID(), Instant.now(), "fam-demo", "misha", "Миша", "req-2", RequestKind.TOPUP,
                "dad", false, Money.of("3"), "На проезд", null, null, null, null, null));
        probe.send(Topics.FAMILY, new RequestResolved(UUID.randomUUID(), Instant.now(), "fam-demo", "misha", "req-1", RequestKind.TOPUP, true,
                Money.of("5"), "На обед", new Actor("mom", "Мама", true), null, null));

        await().atMost(Duration.ofSeconds(30)).until(() -> notifications.count() == 3);
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).until(() -> notifications.count() == 3);
        mvc.perform(get("/api/notifications/feed").with(anna()))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title").value("Миша просит 5,00 BYN"))
                .andExpect(jsonPath("$[0].requestId").value("req-1"));
        mvc.perform(get("/api/notifications/inbox?childId=misha").with(misha()))
                .andExpect(jsonPath("$[0].text").value("Мама одобрила запрос: +5,00 BYN"))
                .andExpect(jsonPath("$[0].seen").value(false));
        mvc.perform(post("/api/notifications/inbox/seen").with(misha()).contentType(MediaType.APPLICATION_JSON).content("{\"childId\":\"misha\"}"))
                .andExpect(jsonPath("$.marked").value(1));
        mvc.perform(get("/api/notifications/inbox?childId=misha").with(anna())).andExpect(jsonPath("$[0].seen").value(true));
    }

    @Test
    @DisplayName("SSE: ребёнок получает своё уведомление, лента ему не приходит")
    void sseDelivery() throws Exception {
        MvcResult stream = mvc.perform(get("/api/notifications/stream").with(misha()))
                .andExpect(request().asyncStarted()).andReturn();
        probe.send(Topics.FAMILY, new RequestResolved(UUID.randomUUID(), Instant.now(), "fam-demo", "misha", "req-9", RequestKind.P2P_OVER, true,
                Money.of("35"), "x", new Actor("mom", "Мама", true), null, null));
        probe.send(Topics.FAMILY, new RequestCreated(UUID.randomUUID(), Instant.now(), "fam-demo", "misha", "Миша", "req-10", RequestKind.TOPUP,
                "mom", true, Money.of("5"), "На обед", null, null, null, null, null));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(stream.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("event:notification").contains("до 35,00 BYN"));
        await().atMost(Duration.ofSeconds(10)).until(() -> notifications.count() == 3);
        assertThat(stream.getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain("просит");
    }

    @Test
    @DisplayName("Лента — только родителю; чужой inbox ребёнку недоступен")
    void access() throws Exception {
        mvc.perform(get("/api/notifications/feed").with(misha())).andExpect(status().isForbidden());
        mvc.perform(get("/api/notifications/inbox?childId=misha").with(ivan())).andExpect(status().isForbidden());
        mvc.perform(post("/api/notifications/inbox/seen").with(anna()).contentType(MediaType.APPLICATION_JSON).content("{\"childId\":\"misha\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/notifications/feed")).andExpect(status().isUnauthorized());
    }
}
