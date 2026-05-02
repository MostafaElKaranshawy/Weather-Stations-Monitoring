package com.example;
import org.json.JSONObject;

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
}
