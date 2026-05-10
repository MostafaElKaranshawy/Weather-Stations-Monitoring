package com.example;

import com.example.adapter.OpenMeteoAdapter;
import com.example.adapter.OpenMeteoClient;

public class Main {
    public static void main(String[] args) {
        OpenMeteoClient client = new OpenMeteoClient();
        OpenMeteoAdapter adapter = new OpenMeteoAdapter();
        try {
            String weatherData = client.fetchWeather();
//            System.out.println(weatherData);
            String adaptedData = adapter.adapt(1, weatherData);
            System.out.println(adaptedData);
            System.out.println();
        } catch (Exception e) {
            System.err.println("Error fetching weather data: " + e.getMessage());
        }
    }
}