package by.moby.family.support;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import tools.jackson.databind.json.JsonMapper;
import by.moby.common.events.IntegrationEvent;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** Читает и пишет события в Kafka так же, как это делают сервисы (заголовки eventType / eventId). */
public final class KafkaProbe {

    private final String bootstrap;
    private final JsonMapper json;

    public KafkaProbe(String bootstrap, JsonMapper json) {
        this.bootstrap = bootstrap;
        this.json = json;
    }

    public void send(String topic, IntegrationEvent event) {
        try (var producer = new KafkaProducer<String, byte[]>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class))) {
            var record = new ProducerRecord<>(topic, event.childId(), json.writeValueAsBytes(event));
            record.headers().add("eventType", event.getClass().getSimpleName().getBytes(StandardCharsets.UTF_8));
            record.headers().add("eventId", event.eventId().toString().getBytes(StandardCharsets.UTF_8));
            producer.send(record).get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Ждёт сообщение, удовлетворяющее условию; возвращает все прочитанные к этому моменту. */
    public List<ConsumerRecord<String, byte[]>> await(String topic, Predicate<ConsumerRecord<String, byte[]>> until, Duration timeout) {
        List<ConsumerRecord<String, byte[]>> seen = new ArrayList<>();
        try (var consumer = new KafkaConsumer<String, byte[]>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap,
                ConsumerConfig.GROUP_ID_CONFIG, "probe-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class))) {
            consumer.subscribe(List.of(topic));
            Instant deadline = Instant.now().plus(timeout);
            while (Instant.now().isBefore(deadline)) {
                for (var r : consumer.poll(Duration.ofMillis(300))) {
                    seen.add(r);
                    if (until.test(r)) {
                        return seen;
                    }
                }
            }
        }
        throw new AssertionError("no matching message in " + topic + " within " + timeout + "; seen " + seen.size());
    }

    public static String header(ConsumerRecord<String, byte[]> r, String name) {
        var h = r.headers().lastHeader(name);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }
}
