package com.example.model;

import lombok.Getter;
import org.json.JSONObject;

@Getter
public class WeatherRecord {

    private final long stationId;
    private final long sNo;
    private final BatteryStatus batteryStatus;
    private final long timestamp;
    private final int humidity;
    private final int temperature;
    private final int windSpeed;

    public WeatherRecord(long stationId, long sNo, BatteryStatus batteryStatus, long timestamp,
                         int humidity, int temperature, int windSpeed) {
        this.stationId = stationId;
        this.sNo = sNo;
        this.batteryStatus = batteryStatus;
        this.timestamp = timestamp;
        this.humidity = humidity;
        this.temperature = temperature;
        this.windSpeed = windSpeed;
    }

    public String toJSON() {
        return new JSONObject().put("station_id", stationId)
                .put("s_no", sNo)
                .put("battery_status", batteryStatus.name().toLowerCase())
                .put("status_timestamp", timestamp)
                .put("humidity", humidity)
                .put("temperature", temperature)
                .put("wind_speed", windSpeed)
                .toString();
    }

}
