package com.example;

import com.example.station.WeatherStation;

public class WeatherStationApp {
    public static void main(String[] args) {
        String stationIdEnv = System.getenv("STATION_ID");
        
        if (stationIdEnv != null && !stationIdEnv.isEmpty()) {
            try {
                long stationId = Long.parseLong(stationIdEnv);
                System.out.println("Starting Weather Station ID: " + stationId);
                new WeatherStation(stationId).run();
            } catch (NumberFormatException e) {
                System.err.println("Invalid STATION_ID: " + stationIdEnv);
                System.exit(1);
            }
        } else {
            // Default behavior: start 10 stations in separate threads
            int numberOfStations = 10;
            System.out.println("No STATION_ID env found. Starting " + numberOfStations + " stations in multi-threaded mode.");
            for (int i = 1; i <= numberOfStations; i++) {
                Thread t = new Thread(new WeatherStation(i));
                t.start();
            }
        }
    }
}
