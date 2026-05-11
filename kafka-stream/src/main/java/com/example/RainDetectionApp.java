package com.example;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;

import java.util.Properties;

public class RainDetectionApp {

    public static void main(String[] args) {

        Properties props = new Properties();

        // Unique identifier for the application, used in Kafka to manage state
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "rain-detector");

        // Kafka broker address to tell the application where to connect
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");

        // Default serializers and deserializers for keys and values, using String format
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG,
                Serdes.String().getClass());

        // Default serializers and deserializers for values, using String format as Kafka stores data as bytes, so we convert it
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG,
                Serdes.String().getClass());

        StreamsBuilder builder = new StreamsBuilder();

        // Step 1: Read from topic
        KStream<String, String> stream =
                builder.stream("weather_data");

        // Step 2: Filter humidity > 70
        KStream<String, String> rainStream = stream.filter(
                (key, value) -> WeatherParser.isRaining(value)
        );

        // Step 3: Send to another topic
        rainStream.to("rain_alerts");

        KafkaStreams streams = new KafkaStreams(builder.build(), props);

        streams.start();

        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
        System.out.println("!!!!");
    }
}