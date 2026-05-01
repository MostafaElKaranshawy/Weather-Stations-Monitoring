package com.example;

import com.example.station.WeatherStation;

public class Main {
    public static void main(String[] args) {
        int numberOfStations = 10;
        for (int i = 1; i <= numberOfStations; i++) {
            Thread t = new Thread(new WeatherStation(i));
            t.start();
        }
    }
}
