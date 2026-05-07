package com.example.storage;

import com.example.model.WeatherRecord;
import com.example.storage.parquet.ParquetArchiver;

public class WeatherStorageCoordinator {

//    private final BitCask bitCask;
    private final ParquetArchiver parquet;

    public WeatherStorageCoordinator() {
//        this.bitCask = new BitCask();
        this.parquet = new ParquetArchiver();
    }

    public void save(WeatherRecord weatherRecord) throws Exception {
        String key = String.valueOf(weatherRecord.getStationId());
        byte[] bytes = weatherRecord.toJSON().getBytes();

        // Store in BitCask
//        bitCask.put(key, bytes);

        // Archive in Parquet
        parquet.archive(weatherRecord);

    }

}
