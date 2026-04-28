package com.example.weather_station.station;

import com.example.weather_station.generator.WeatherDataGenerator;
import com.example.weather_station.producer.KafkaProducerService;

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
            sequence++;

            generator.generate(stationId, sequence).ifPresent(message -> {
                String json = message.toJson();
                // send to Kafka
                producer.send(String.valueOf(stationId), json);
            });

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}