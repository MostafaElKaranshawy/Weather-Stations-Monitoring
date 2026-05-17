package com.example.model;

public class Weather {
    private int humidity;
    private int temperature;
    private int windSpeed;

    public Weather(int humidity, int temperature, int windSpeed) {
        this.humidity = humidity;
        this.temperature = temperature;
        this.windSpeed = windSpeed;
    }

    // Getters
    public int getHumidity() { return humidity; }
    public int getTemperature() { return temperature; }
    public int getWindSpeed() { return windSpeed; }
}