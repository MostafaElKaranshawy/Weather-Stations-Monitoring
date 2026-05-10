package com.example.adapter;

import org.json.JSONObject;

public class OpenMeteoAdapter {

    public String adapt(long sNo, String apiResponse) {

        JSONObject obj = new JSONObject(apiResponse);

        JSONObject current = obj.getJSONObject("current");

        int temperature = (int) current.getDouble("temperature_2m"); // converted into int to match other stations format

        int humidity = current.getInt("relative_humidity_2m");

        int windSpeed = (int) current.getDouble("wind_speed_10m"); // converted into int to match other stations format

        JSONObject metadata = new JSONObject()
                    .put("station_id", 11) // fixed station id for OpenMeteo
                    .put("s_no", sNo)
                    .put("battery_status", "n/a") // OpenMeteo doesn't have battery status as it's an external API
                    .put("status_timestamp", System.currentTimeMillis());

        JSONObject weatherData = new JSONObject()
                    .put("humidity", humidity)
                    .put("temperature", temperature)
                    .put("wind_speed", windSpeed);
        return new JSONObject()
                    .put("metadata", metadata)
                    .put("payload", new JSONObject().put("weather", weatherData))
                    .toString();
    }
}