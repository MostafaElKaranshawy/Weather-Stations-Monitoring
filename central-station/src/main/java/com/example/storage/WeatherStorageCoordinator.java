package com.example.storage;

import com.example.model.WeatherRecord;
import com.example.storage.parquet.ParquetArchiver;

public class WeatherStorageCoordinator {

//    private final BitCask bitCask;
    private final ParquetArchiver parquet;

    // add a second argument BitCask bitCask to the constructor once we have it set up
    public WeatherStorageCoordinator(ParquetArchiver parquetArchiver) {
//        this.bitCask = bitcask
        this.parquet = parquetArchiver;
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
