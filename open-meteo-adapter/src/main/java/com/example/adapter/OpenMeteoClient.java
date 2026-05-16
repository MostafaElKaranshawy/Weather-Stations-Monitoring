package com.example.adapter;

import io.github.cdimascio.dotenv.Dotenv;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;

public class OpenMeteoClient {

    private static final Dotenv dotenv = Dotenv.load();

    // Singleton client to reuse the TCP/TLS connection pool across all requests
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2) // Enforce efficient HTTP/2 protocol
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    static String latitude = dotenv.get("WEATHER_API_LATITUDE");
    static String longitude = dotenv.get("WEATHER_API_LONGITUDE");
    // Constant URL string so that it can be pre-parsed and optimized by the JVM
    private static final String WEATHER_URL = "https://api.open-meteo.com/v1/forecast"
            + "?latitude=" + latitude
                + "&longitude=" + longitude
                + "&current=temperature_2m,relative_humidity_2m,wind_speed_10m";

    public String fetchWeather() throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(WEATHER_URL))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        // Blocking execution using the optimized global connection pool
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        return response.body();
    }
}
