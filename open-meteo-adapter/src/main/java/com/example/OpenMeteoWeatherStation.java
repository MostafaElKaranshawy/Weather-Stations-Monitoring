package com.example;

import com.example.adapter.OpenMeteoAdapter;
import com.example.adapter.OpenMeteoClient;
import com.example.producer.KafkaProducerService;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class OpenMeteoWeatherStation {
    
    static Dotenv dotenv = Dotenv.load();
    private static final long STATION_ID = Long.parseLong(dotenv.get("STATION_ID"));
    private static final String SEQUENCE_FILE = dotenv.get("SEQUENCE_FILE");
    private long sequence = 0;
    private int requestCounter = 0;
    private  static final int REQUEST_INTERVAL = Integer.parseInt(dotenv.get("REQUEST_INTERVAL"));
    private static final int BACKUP_INTERVAL_REQUESTS = Integer.parseInt(dotenv.get("BACKUP_INTERVAL_REQUESTS")); 
    private final OpenMeteoClient client = new OpenMeteoClient();
    private final OpenMeteoAdapter adapter = new OpenMeteoAdapter();
    private final KafkaProducerService producer = new KafkaProducerService();

    public OpenMeteoWeatherStation() {
        this.sequence = loadSequence();
    }

    public void runPipelineStep() throws Exception {
        this.sequence++;
        this.requestCounter++; // Increment counter on every execution
        String apiResponse = client.fetchWeather();
        String adaptedMessage = adapter.adapt(this.sequence, apiResponse);
        producer.send(String.valueOf(STATION_ID), adaptedMessage);
        // Save to file exactly every 6 requests (6 * 10 seconds = 1 minute)
        if (this.requestCounter >= BACKUP_INTERVAL_REQUESTS) {
            saveSequence();
            this.requestCounter = 0; // Reset counter
        }
    }

    public synchronized void saveSequence() {
        try {
            Files.writeString(Paths.get(SEQUENCE_FILE), String.valueOf(this.sequence));
            System.out.println("Sequence " + this.sequence + " backed up to " + SEQUENCE_FILE);
        } catch (IOException e) {
            System.err.println("Failed to save sequence number: " + e.getMessage());
        }
    }

    private long loadSequence() {
        try {
            if (Files.exists(Paths.get(SEQUENCE_FILE))) {
                String content = Files.readString(Paths.get(SEQUENCE_FILE)).trim();
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

    public static void main(String[] args) {
        OpenMeteoWeatherStation station = new OpenMeteoWeatherStation();

        // Add shutdown hook to handle stops, crashes, or termination cleanly
        Runtime.getRuntime().addShutdownHook(new Thread(station::close));

        while (!Thread.currentThread().isInterrupted()) {
            long startTime = System.currentTimeMillis();

            try {
                station.runPipelineStep();
            } catch (Exception e) {
                System.err.println("Execution pipeline failure: " + e.getMessage());
                e.printStackTrace();
            }

            long elapsedTime = System.currentTimeMillis() - startTime;
            long sleepTime = REQUEST_INTERVAL - elapsedTime; // 10 second intervals

            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    System.err.println("Main processing thread interrupted. Exiting loop.");
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
