package com.example.adapter;
import com.example.model.BatteryStatus;
import com.example.model.Weather;
import com.example.model.WeatherMessage;
import io.github.cdimascio.dotenv.Dotenv;
import org.json.JSONObject;

public class OpenMeteoAdapter {

    private static final Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
    private static final long STATION_ID = Long.parseLong(dotenv.get("STATION_ID") != null ? dotenv.get("STATION_ID") : System.getenv().getOrDefault("STATION_ID", ""));

    public String adapt(long sNo, String apiResponse) {

        JSONObject obj = new JSONObject(apiResponse);

        JSONObject current = obj.getJSONObject("current");

        int temperature = (int) current.getDouble("temperature_2m"); // converted into int to match other stations format

        int humidity = current.getInt("relative_humidity_2m");

        int windSpeed = (int) current.getDouble("wind_speed_10m"); // converted into int to match other stations format

        Weather weather = new Weather(humidity, temperature, windSpeed);

        WeatherMessage message = new WeatherMessage(STATION_ID, sNo, BatteryStatus.NA, System.currentTimeMillis(), weather);
        return message.toJson();
    }
}
