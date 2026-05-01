package com.example.generator;

import com.example.model.BatteryStatus;
import com.example.model.Weather;
import com.example.model.WeatherMessage;

import java.util.Optional;
import java.util.Random;

public class WeatherDataGenerator {

    private final Random random = new Random();

    public Optional<WeatherMessage> generate(long stationId, long sNo) {

        // dropping 10% of messages
        if (random.nextDouble() < 0.1) { // picking a number between 0 and 1
            return Optional.empty();
        }

        BatteryStatus batteryStatus = generateBatteryStatus();

        Weather weather = new Weather(
                random.nextInt(101),     // humidity 0–100
                random.nextInt(121),     // temperature 0-120
                random.nextInt(51)       // wind speed 0-50
        );

        WeatherMessage message = new WeatherMessage(
                stationId,
                sNo,
                batteryStatus,
                System.currentTimeMillis() / 1000,
                weather
        );

        return Optional.of(message);
    }

    private BatteryStatus generateBatteryStatus() {
        double r = random.nextDouble(); // picking a number between 0 and 1 for battery status

        if (r < 0.3) return BatteryStatus.LOW;
        else if (r < 0.7) return BatteryStatus.MEDIUM;
        else return BatteryStatus.HIGH;
    }
}