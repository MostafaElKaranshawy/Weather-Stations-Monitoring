package com.example.router;

import io.github.cdimascio.dotenv.Dotenv;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class FailureMessageRouter implements AutoCloseable {

    static Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
    private static final String INVALID_TOPIC = dotenv.get("INVALID_TOPIC") != null ? dotenv.get("INVALID_TOPIC") : System.getenv().getOrDefault("INVALID_TOPIC", "");
    private static final String DEAD_LETTER_TOPIC = dotenv.get("DEAD_LETTER_TOPIC") != null ? dotenv.get("DEAD_LETTER_TOPIC") : System.getenv().getOrDefault("DEAD_LETTER_TOPIC", "");

    private final KafkaProducer<String, String> producer;

    public FailureMessageRouter() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, dotenv.get("KAFKA_BOOTSTRAP_SERVERS") != null ? dotenv.get("KAFKA_BOOTSTRAP_SERVERS") : System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", ""));
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        this.producer = new KafkaProducer<>(props);
    }

    // this is needed for debugging so we need to send extra info/logs
    public void sendToInvalidChannel(String rawJSON) { send(INVALID_TOPIC, rawJSON); }

    public void sendToDeadLetterChannel(String rawJSON) {
        send(DEAD_LETTER_TOPIC, rawJSON);
    }

    private void send(String topic, String rawJSON) {
        String key = extractKey(rawJSON);

        producer.send(new ProducerRecord<>(topic, key, rawJSON), (metadata, ex) -> {
            if (ex != null)
                System.err.printf("[FailureRouter] send failed — topic=%s key=%s error=%s%n",
                        topic, key, ex.getMessage());

            else
                System.out.printf("[FailureRouter] routed — topic=%s key=%s partition=%d offset=%d%n",
                        topic, key, metadata.partition(), metadata.offset());

        });
    }

    @Override
    public void close() {
        System.out.println("[FailureMessageRouter] Closing failure router producer connections...");
        if (producer != null) {
            try {
                // Wait up to 5 seconds to flush outstanding error messages
                producer.close(java.time.Duration.ofSeconds(5));
                System.out.println("[FailureMessageRouter] Closed successfully.");
            } catch (Exception e) {
                System.err.println("[FailureMessageRouter] Error closing producer: " + e.getMessage());
            }
        }
    }

    private String extractKey(String json) {
        try {
            JSONObject metadata = new JSONObject(json).getJSONObject("metadata");
            return metadata.getLong("station_id") + "-" + metadata.getLong("s_no");
        }
        catch (Exception e) {
            // should discuss how to handle this failure for now
            return json;
        }
    }
}
