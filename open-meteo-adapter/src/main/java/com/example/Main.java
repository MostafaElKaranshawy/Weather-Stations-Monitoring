package com.example;

import com.example.adapter.OpenMeteoAdapter;
import com.example.adapter.OpenMeteoClient;

public class Main {
    public static void main(String[] args) {
        OpenMeteoClient client = new OpenMeteoClient();
        OpenMeteoAdapter adapter = new OpenMeteoAdapter();
        try {
            long start = System.currentTimeMillis();
            String weatherData = client.fetchWeather();
            long end1 = System.currentTimeMillis();
            String adaptedData = adapter.adapt(1, weatherData);
            long end2 = System.currentTimeMillis();
            System.out.println("Time taken to fetch data: " + (end1 - start) + " ms");
            System.out.println("Time taken to adapt data: " + (end2 - end1) + " ms");
            System.out.println(weatherData);
            System.out.println(adaptedData);
        } catch (Exception e) {
            System.err.println("Error fetching weather data: " + e.getMessage());
        }
    }
}