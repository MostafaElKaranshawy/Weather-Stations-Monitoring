package com.example.weather_station.model;

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
        return String.format(
                "{\"station_id\":%d,\"s_no\":%d,\"battery_status\":\"%s\",\"status_timestamp\":%d," +
                        "\"weather\":{\"humidity\":%d,\"temperature\":%d,\"wind_speed\":%d}}",
                stationId, sNo, batteryStatus.name().toLowerCase(), timestamp,
                weather.getHumidity(), weather.getTemperature(), weather.getWindSpeed()
        );
    }
}