package com.example;

import com.example.consumer.WeatherKafkaConsumer;
import com.example.storage.WeatherStorageCoordinator;
import com.example.storage.parquet.ParquetArchiver;

public class CentralStationApp {

    public static void main(String[] args) throws Exception {
        // will need to uncomment these if we set up config file for them
//        String bootstrapServers = System.getenv()
//                .getOrDefault("KAFKA_BOOTSTRAP", "localhost:9092");
//        String bitcaskDir = System.getenv()
//                .getOrDefault("BITCASK_DIR", "/data/bitcask");

//        BitCaskStore      bitCask    = new BitCaskStore(bitcaskDir);
        ParquetArchiver   parquet    = new ParquetArchiver();
        WeatherStorageCoordinator coordinator = new WeatherStorageCoordinator(parquet);


        WeatherKafkaConsumer consumer =
                new WeatherKafkaConsumer(coordinator);

        // Runs forever on the main thread — Ctrl+C to stop
        // You will see validation results, dispatch calls, and any errors printed
        consumer.run();
    }
}