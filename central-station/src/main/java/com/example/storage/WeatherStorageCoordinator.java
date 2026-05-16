package com.example.storage;

import com.example.model.WeatherRecord;
import com.example.storage.bitcask.BitCaskStore;
import com.example.storage.parquet.ParquetArchiver;

public class WeatherStorageCoordinator {

    private final BitCaskStore bitCask;
    private final ParquetArchiver parquet;

    public WeatherStorageCoordinator(BitCaskStore bitCask, ParquetArchiver parquetArchiver) {
        this.bitCask = bitCask;
        this.parquet = parquetArchiver;
    }

    public void save(WeatherRecord weatherRecord) throws Exception {
        String key = String.valueOf(weatherRecord.getStationId());
        byte[] bytes = weatherRecord.toJSON().getBytes();

        // Store in BitCask (latest state)
        bitCask.put(key, bytes);

        // Archive in Parquet (historical)
        parquet.archive(weatherRecord);
    }

}
