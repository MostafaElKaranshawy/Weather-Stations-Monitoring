package com.example.router;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class FailureMessageRouter implements AutoCloseable {

    private static final String INVALID_TOPIC = "weather_invalid_data";
    private static final String DEAD_LETTER_TOPIC = "weather_dead_letter";

    private final KafkaProducer<String, String> producer;

    public FailureMessageRouter() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:9092");
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
        producer.flush();
        producer.close();
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
