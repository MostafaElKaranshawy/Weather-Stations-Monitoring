package com.example.weather_station;

import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;

public class TestKafkaProducer {

    public static void main(String[] args) {

        Properties properties = new Properties();

        properties.setProperty(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                "127.0.0.1:9092"
        );

        properties.setProperty(
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class.getName()
        );

        properties.setProperty(
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class.getName()
        );

        try (KafkaProducer<String, String> producer =
                     new KafkaProducer<>(properties)) {

            ProducerRecord<String, String> record =
                    new ProducerRecord<>("my_first", "Hey Kafka!");

            producer.send(record);

            System.out.println("Message sent!");
        }
    }
}