package com.example.station;

import com.example.generator.WeatherDataGenerator;
import com.example.producer.KafkaProducerService;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class WeatherStation implements Runnable {

    static Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
    private final long stationId;
    private String sequenceFile;
    private long sequence = 0;
    private int requestCounter = 0;
    private static final long REQUEST_INTERVAL  = Long.parseLong(dotenv.get("REQUEST_INTERVAL") != null ? dotenv.get("REQUEST_INTERVAL") : System.getenv().getOrDefault("REQUEST_INTERVAL", ""));
    private static final int BACKUP_INTERVAL_REQUESTS = Integer.parseInt(dotenv.get("BACKUP_INTERVAL_REQUESTS") != null ? dotenv.get("BACKUP_INTERVAL_REQUESTS") : System.getenv().getOrDefault("BACKUP_INTERVAL_REQUESTS", ""));

    private final WeatherDataGenerator generator = new WeatherDataGenerator();
    private final KafkaProducerService producer = new KafkaProducerService();

    public WeatherStation(long stationId) {
        this.stationId = stationId;
        String dataDir = dotenv.get("DATA_DIR") != null 
            ? dotenv.get("DATA_DIR") 
            : System.getenv().getOrDefault("DATA_DIR", ".");
        String sequenceFileName = dotenv.get("SEQUENCE_FILE") != null 
            ? dotenv.get("SEQUENCE_FILE") 
            : System.getenv().getOrDefault("SEQUENCE_FILE", "sequence.txt");
        this.sequenceFile = dataDir + "/station_" + stationId + "_" + sequenceFileName;
        this.sequence = loadSequence();
    }

    public synchronized void saveSequence() {
        try {
            Files.writeString(Paths.get(sequenceFile), String.valueOf(this.sequence));
            System.out.println("Sequence " + this.sequence + " backed up to " + sequenceFile);
        } catch (IOException e) {
            System.err.println("Failed to save sequence number: " + e.getMessage());
        }
    }

    private long loadSequence() {
        try {
            if (Files.exists(Paths.get(sequenceFile))) {
                String content = Files.readString(Paths.get(sequenceFile)).trim();
                return Long.parseLong(content);
            }
        } catch (Exception e) {
            System.err.println("Could not load sequence file, starting from 0. Error: " + e.getMessage());
        }
        return 0;
    }

    public void close() {
        System.out.println("Executing application cleanup...");
        saveSequence(); // Final save of the exact current sequence before shutdown
        producer.close();
        System.out.println("Cleanup complete. Application is shutting down.");
    }

    @Override
    public void run() {

        // Add shutdown hook to handle stops, crashes, or termination cleanly
        Runtime.getRuntime().addShutdownHook(new Thread(this::close));

        while (true) {
            long startTime = System.currentTimeMillis();
            this.sequence++;
            this.requestCounter++;
            generator.generate(stationId, sequence).ifPresent(message -> {
                String json = message.toJson();
                producer.send(String.valueOf(stationId), json);
            });
            // Backup sequence number after every BACKUP_INTERVAL_REQUESTS requests
            if (requestCounter >= BACKUP_INTERVAL_REQUESTS) {
                saveSequence();
                requestCounter = 0; // reset counter after backup
            }
            long elapsedTime = System.currentTimeMillis() - startTime; // almost zero, only around half a second at first time
            long sleepTime = REQUEST_INTERVAL  - elapsedTime; //remaining time to sleep

            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}