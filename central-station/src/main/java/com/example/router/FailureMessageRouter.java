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

    private static final int MAX_RETRIES = 5; // a parameter to be chosen
    private static final String INVALID_TOPIC = "weather_invalid_data";
    private static final String DEAD_LETTER_TOPIC = "weather_dead_letter";

    private final Map<String, Integer> failureCount = new HashMap<>();
    private final KafkaProducer<String, String> producer;

    public FailureMessageRouter() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        this.producer = new KafkaProducer<>(props);
    }

    public void route(String rawJSON) {
        String messageKey = extractKey(rawJSON);
        int count = failureCount.merge(messageKey, 1, Integer::sum);
        String targetTopic = count > MAX_RETRIES ? DEAD_LETTER_TOPIC : INVALID_TOPIC;

        producer.send(new ProducerRecord<>(targetTopic, messageKey, rawJSON),
            (metadata, exception) -> {
                if (exception != null)
                    System.err.println("Failed to route message to " + targetTopic + " : " + exception.getMessage());
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
