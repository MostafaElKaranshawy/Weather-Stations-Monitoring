package com.example;
import io.github.cdimascio.dotenv.Dotenv;
import org.json.JSONObject;

import java.time.Duration;
import java.time.Instant;

public class WeatherParser {

    public static boolean isRaining(String json) {
        try {
            JSONObject obj = new JSONObject(json);

            // 1. Get the "payload" object
            JSONObject payload = obj.getJSONObject("payload");

            // 2. Get the "weather" object inside payload
            JSONObject weather = payload.getJSONObject("weather");

            // 3. Extract humidity
            int humidity = weather.getInt("humidity");

            return humidity > 70;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isTimeValid(String json) {
        Dotenv dotenv = Dotenv.load();
        long maxRecordAgeHours = Long.parseLong(dotenv.get("MAX_RECORD_AGE_HOURS"));
        JSONObject obj = new JSONObject(json);
        JSONObject metadata = obj.getJSONObject("metadata");

        try {

            long recordTimestampMs = metadata.getLong("status_timestamp");
            Instant recordTime = Instant.ofEpochMilli(recordTimestampMs);
            Instant now = Instant.now();

            // Reject future timestamps
            if (recordTime.isAfter(now)) {
                return false;
            }

            // Reject records older than maxRecordAgeHours
            long hoursAgo = Duration.between(recordTime, now).toHours();
            if (hoursAgo >= maxRecordAgeHours) {
                return false;
            }

            return true;
        } catch (Exception e) {
            // If timestamp is missing or malformed, reject the message
            return false;
        }
    }
}
