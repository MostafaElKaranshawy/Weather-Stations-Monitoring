package com.example;

import com.example.adapter.OpenMeteoClient;

public class Main {
    public static void main(String[] args) {
        OpenMeteoClient client = new OpenMeteoClient();
        try {
            String weatherData = client.fetchWeather();
            System.out.println(weatherData);
        } catch (Exception e) {
            System.err.println("Error fetching weather data: " + e.getMessage());
        }
    }
}