package com.example;

import com.example.adapter.OpenMeteoAdapter;
import com.example.adapter.OpenMeteoClient;
import com.example.producer.KafkaProducerService;

public class OpenMeteoWeatherStation {

    private static final long STATION_ID = -1;
    private long sequence = 0;

    private final OpenMeteoClient client = new OpenMeteoClient();
    private final OpenMeteoAdapter adapter = new OpenMeteoAdapter();
    private final KafkaProducerService producer = new KafkaProducerService();

    public void runPipelineStep() throws Exception {
        this.sequence++;

        String apiResponse = this.client.fetchWeather();
        String adaptedMessage = this.adapter.adapt(this.sequence, apiResponse);
        this.producer.send(String.valueOf(STATION_ID), adaptedMessage);
    }

    public static void main(String[] args) {
        OpenMeteoWeatherStation station = new OpenMeteoWeatherStation();

        while (true) {
            long startTime = System.currentTimeMillis();

            try {
                station.runPipelineStep();
            } catch (Exception e) {
                System.err.println("Execution pipeline failure: " + e.getMessage());
                e.printStackTrace();
            }

            long elapsedTime = System.currentTimeMillis() - startTime;
            long sleepTime = 1000 - elapsedTime;

            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    System.err.println("Main processing thread interrupted. Exiting loop.");
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
}
