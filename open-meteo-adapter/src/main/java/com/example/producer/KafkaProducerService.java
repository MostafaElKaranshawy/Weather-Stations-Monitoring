package com.example.producer;

import io.github.cdimascio.dotenv.Dotenv;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;

public class KafkaProducerService {

    private static final Dotenv dotenv = Dotenv.load();
    private final KafkaProducer<String, String> producer;
    private final String topic = dotenv.get("KAFKA_TOPIC");

    public KafkaProducerService() {
        Properties props = new Properties();

        props.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, dotenv.get("KAFKA_BOOTSTRAP_SERVERS"));
        props.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        producer = new KafkaProducer<>(props);
    }

    public void send(String key, String value) {
        //As we have station-id as key, so all messages from the same station will go to the same partition, and we can process them in order
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value); // it is the envelope of the message, it contains the topic, key(station-id) and data
        producer.send(record);
    }

    public void close() {
        producer.flush(); // Force send everything in memory
        producer.close(); // Close the connection
    }
}