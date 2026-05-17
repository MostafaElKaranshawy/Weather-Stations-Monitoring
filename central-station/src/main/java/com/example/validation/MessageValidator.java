package com.example.validation;

import io.github.cdimascio.dotenv.Dotenv;
import org.json.JSONObject;

import java.time.Duration;
import java.time.Instant;

public class MessageValidator {

    public boolean isValidJSON(String json) {
        if (json == null || json.trim().isEmpty())
            return false;

        try {
            JSONObject obj = new JSONObject(json);

            return hasValidStructure(obj) &&
                    hasValidMetadata(obj.getJSONObject("metadata")) &&
                    hasValidPayload(obj.getJSONObject("payload")) && isTimeValid(json);

        } catch (Exception e) {
            return false; // Invalid JSON or missing required objects
        }
    }

    private boolean hasValidStructure(JSONObject obj) {
        return obj.has("metadata") && obj.has("payload");
    }

    private boolean hasValidMetadata(JSONObject metadata) {
        return hasRequiredMetadataFields(metadata) &&
                hasValidMetadataTypes(metadata) &&
                hasValidBatteryStatus(metadata);
    }

    private boolean hasRequiredMetadataFields(JSONObject metadata) {
        return metadata.has("station_id") &&
                metadata.has("s_no") &&
                metadata.has("battery_status") &&
                metadata.has("status_timestamp");
    }

    private boolean hasValidMetadataTypes(JSONObject metadata) {
        return isNumber(metadata, "station_id") &&
                isNumber(metadata, "s_no") &&
                isNumber(metadata, "status_timestamp");
    }

    private boolean hasValidBatteryStatus(JSONObject metadata) {
        try {
            String status = metadata.getString("battery_status");
            if (status == null) return false;
            String lower = status.toLowerCase().trim();
            return "low".equals(lower) || "medium".equals(lower) || "high".equals(lower) || "na".equals(lower);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasValidPayload(JSONObject payload) {
        if (!payload.has("weather"))
            return false;

        JSONObject weather = payload.getJSONObject("weather");

        return isNumber(weather, "humidity") &&
                isNumber(weather, "temperature") &&
                isNumber(weather, "wind_speed") &&
                hasValidWeatherValues(weather);
    }

    private boolean hasValidWeatherValues(JSONObject weather) {
        try {
            int humidity = weather.getInt("humidity");
            int temperature = weather.getInt("temperature");
            int windSpeed = weather.getInt("wind_speed");

            return humidity >= 0 && humidity <= 100 &&
                    temperature >= 0 && temperature <= 120 &&
                    windSpeed >= 0 && windSpeed <= 50;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isNumber(JSONObject obj, String key) {
        Object value = obj.opt(key);
        return value instanceof Number;
    }

    private boolean isTimeValid(String json) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        long maxRecordAgeHours = Long.parseLong(dotenv.get("MAX_RECORD_AGE_HOURS") != null ? dotenv.get("MAX_RECORD_AGE_HOURS") : System.getenv().getOrDefault("MAX_RECORD_AGE_HOURS", ""));
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
            return hoursAgo < maxRecordAgeHours;
        } catch (Exception e) {
            // If timestamp is missing or malformed, reject the message
            return false;
        }
    }

}