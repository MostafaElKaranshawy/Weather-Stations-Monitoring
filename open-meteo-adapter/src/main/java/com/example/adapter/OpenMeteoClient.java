package com.example.adapter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class OpenMeteoClient {

    public String fetchWeather() throws Exception {

        String endpoint =
                "https://api.open-meteo.com/v1/forecast"
                        + "?latitude=31.2000"
                        + "&longitude=29.8999"
                        + "&current=temperature_2m,relative_humidity_2m,wind_speed_10m";

        URL url = new URL(endpoint);

        HttpURLConnection connection =
                (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("GET");

        BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(connection.getInputStream())
                );

        StringBuilder response = new StringBuilder();

        String line;

        while ((line = reader.readLine()) != null) {
            response.append(line);
        }

        reader.close();

        return response.toString();
    }
}