package com.example.generator;

import com.example.model.BatteryStatus;
import com.example.model.Weather;
import com.example.model.WeatherMessage;

import java.util.Optional;
import java.util.Random;

public class WeatherDataGenerator {

    private final Random random = new Random();

    public Optional<WeatherMessage> generate(long stationId, long sNo) {
        // Generates a weather message with random values for humidity, temperature, and wind speed
        WeatherMessage message = new WeatherMessage(
                stationId,
                sNo,
                generateBatteryStatus(),
                System.currentTimeMillis(),
                new Weather(random.nextInt(101), random.nextInt(121), random.nextInt(51))
        );

        // Simulates a 10% chance of failure to generate a message
        if (random.nextDouble() < 0.1) {
            return Optional.empty();
        }

        return Optional.of(message);
    }


    private BatteryStatus generateBatteryStatus() {
        double r = random.nextDouble(); // picking a number between 0 and 1 for battery status

        if (r < 0.3) return BatteryStatus.LOW;
        else if (r < 0.7) return BatteryStatus.MEDIUM;
        else return BatteryStatus.HIGH;
    }
}