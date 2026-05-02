package com.example.station;

import com.example.generator.WeatherDataGenerator;
import com.example.producer.KafkaProducerService;

public class WeatherStation implements Runnable {

    private final long stationId;
    private long sequence = 0;

    private final WeatherDataGenerator generator = new WeatherDataGenerator();
    private final KafkaProducerService producer = new KafkaProducerService();

    public WeatherStation(long stationId) {
        this.stationId = stationId;
    }

    @Override
    public void run() {
        while (true) {
            long startTime = System.currentTimeMillis();
            sequence++;

            generator.generate(stationId, sequence).ifPresent(message -> {
                String json = message.toJson();
                // send to Kafka
                producer.send(String.valueOf(stationId), json);
            });

            long elapsedTime = System.currentTimeMillis() - startTime; // almost zero, only around half a second at first time
            long sleepTime = 1000 - elapsedTime; //remaining time to sleep
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