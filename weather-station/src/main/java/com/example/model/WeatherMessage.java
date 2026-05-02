package com.example.model;

import org.json.JSONObject;

public class WeatherMessage {
    private long stationId;
    private long sNo;
    private BatteryStatus batteryStatus;
    private long timestamp;
    private Weather weather;

    public WeatherMessage(long stationId, long sNo, BatteryStatus batteryStatus,
                          long timestamp, Weather weather) {
        this.stationId = stationId;
        this.sNo = sNo;
        this.batteryStatus = batteryStatus;
        this.timestamp = timestamp;
        this.weather = weather;
    }

    public String toJson() {

        JSONObject metadata = new JSONObject()
                .put("station_id", stationId)
                .put("s_no", sNo)
                .put("battery_status", batteryStatus.name().toLowerCase())
                .put("status_timestamp", timestamp);

        JSONObject weatherData = new JSONObject()
                .put("humidity", weather.getHumidity())
                .put("temperature", weather.getTemperature())
                .put("wind_speed", weather.getWindSpeed());

        return new JSONObject()
                .put("metadata", metadata)
                .put("payload", new JSONObject().put("weather", weatherData))
                .toString();
    }

}