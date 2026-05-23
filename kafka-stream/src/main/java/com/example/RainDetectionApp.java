package com.example;

import io.github.cdimascio.dotenv.Dotenv;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;

import java.time.Duration;
import java.util.Properties;

public class RainDetectionApp {

    public static void main(String[] args) {
        // Configure dotenv to safely handle environments without a physical .env file
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

        // Read configuration from environment variables
        String applicationId = dotenv.get("APPLICATION_ID_CONFIG") != null ? dotenv.get("APPLICATION_ID_CONFIG") : System.getenv().getOrDefault("APPLICATION_ID_CONFIG", "");
        String bootstrapServers = dotenv.get("BOOTSTRAP_SERVERS_CONFIG") != null ? dotenv.get("BOOTSTRAP_SERVERS_CONFIG") : System.getenv().getOrDefault("BOOTSTRAP_SERVERS_CONFIG", "");
        String inputTopic = dotenv.get("INPUT_TOPIC") != null ? dotenv.get("INPUT_TOPIC") : System.getenv().getOrDefault("INPUT_TOPIC", "");
        String outputTopic = dotenv.get("OUTPUT_TOPIC") != null ? dotenv.get("OUTPUT_TOPIC") : System.getenv().getOrDefault("OUTPUT_TOPIC", "");

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, applicationId);
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

        StreamsBuilder builder = new StreamsBuilder();

        // Step 1: Read from topic
        KStream<String, String> stream = builder.stream(inputTopic);

        // Step 2: Filter humidity > 70
        KStream<String, String> rainStream = stream.filter(
                (key, value) -> (WeatherParser.isTimeValid(value) && WeatherParser.isRaining(value))
        );

        // Step 3: Send to another topic
        rainStream.to(outputTopic);
        KafkaStreams streams = new KafkaStreams(builder.build(), props);

        // Shutdown Hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown signal caught. Closing Kafka Streams...");
            streams.close(Duration.ofSeconds(10));
            System.out.println("Kafka Streams closed successfully.");
        }, "streams-shutdown-hook"));

        try {
            streams.start();
        } catch (Throwable e) {
            System.err.println("Application encountered a fatal error: " + e.getMessage());
            System.exit(1);
        }
    }
}
