package com.example.router;

import com.example.model.BatteryStatus;
import com.example.model.WeatherRecord;
import org.json.JSONObject;

import java.util.Optional;

public class EnvelopeUnwrapper {

    public Optional<WeatherRecord> unwrap(String json) {
        try {
            // 1. Parse the JSON string into a JSONObject
            JSONObject mainObject = new JSONObject(json);
            JSONObject metadata = mainObject.getJSONObject("metadata");
            JSONObject weather = mainObject.getJSONObject("payload").getJSONObject("weather");

            return Optional.of(new WeatherRecord(
                    metadata.getLong("station_id"),
                    metadata.getLong("s_no"),
                    BatteryStatus.valueOf(
                            metadata.getString("battery_status").toUpperCase()
                    ),
                    metadata.getLong("status_timestamp"),
                    weather.getInt("humidity"),
                    weather.getInt("temperature"),
                    weather.getInt("wind_speed")
            ));
        }
        catch (Exception e) {
            System.err.println("Failed to unwrap JSON: " + e.getMessage());
            return Optional.empty();
        }
    }


}
