package by.moby.payment.messaging;

import by.moby.common.events.Topics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import by.moby.payment.payments.QrPaymentResult;
import by.moby.payment.payments.QrPaymentService;
import by.moby.payment.support.KafkaProbe;
import by.moby.payment.support.KafkaTestcontainersConfig;
import by.moby.payment.support.PaymentIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Outbox: событие из бизнес-транзакции доходит до Kafka, публикация в event_publication помечается завершённой. */
@Import(KafkaTestcontainersConfig.class)
@TestPropertySource(properties = {
        "spring.modulith.events.externalization.enabled=true",
        "spring.kafka.admin.auto-create=true"})
class PaymentOutboxKafkaTest extends PaymentIntegrationTest {

    @Autowired
    QrPaymentService payments;
    @Autowired
    KafkaContainer kafka;
    @Autowired
    JsonMapper json;
    @Autowired
    JdbcClient jdbc;

    @Test
    @DisplayName("PaymentCompleted и PaymentDeclined уходят в payment.events с ключом childId")
    void paymentEventsReachKafka() {
        QrPaymentResult ok = payments.pay("misha", "m-buffet", UUID.randomUUID().toString());
        QrPaymentResult declined = payments.pay("misha", "m-slots", UUID.randomUUID().toString());
        KafkaProbe probe = new KafkaProbe(kafka.getBootstrapServers(), json);

        Set<String> wanted = Set.of(ok.operationId(), declined.operationId());
        Set<String> found = new HashSet<>();
        var seen = probe.await(Topics.PAYMENT, r -> {
            String op = field(r, "operationId");
            if (wanted.contains(op)) {
                found.add(op);
            }
            return found.containsAll(wanted);
        }, Duration.ofSeconds(30));
        var completed = byOperation(seen, ok.operationId());
        assertThat(KafkaProbe.header(completed, "eventType")).isEqualTo("PaymentCompleted");
        assertThat(completed.key()).isEqualTo("misha");
        assertThat(json.readTree(completed.value()).get("amount").decimalValue()).isEqualByComparingTo("2.40");
        assertThat(KafkaProbe.header(byOperation(seen, declined.operationId()), "eventType")).isEqualTo("PaymentDeclined");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(
                jdbc.sql("select count(*) from event_publication where completion_date is null").query(Long.class).single()).isZero());
    }

    private ConsumerRecord<String, byte[]> byOperation(List<ConsumerRecord<String, byte[]>> records, String operationId) {
        return records.stream().filter(r -> operationId.equals(field(r, "operationId"))).findFirst().orElseThrow();
    }

    private String field(ConsumerRecord<String, byte[]> r, String name) {
        JsonNode node = json.readTree(r.value());
        return node.hasNonNull(name) ? node.get(name).asString() : null;
    }
}
