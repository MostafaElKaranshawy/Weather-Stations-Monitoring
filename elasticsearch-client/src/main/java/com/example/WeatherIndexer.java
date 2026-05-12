package com.example;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.example.model.BatteryStatus;
import com.example.model.WeatherRecord;
import com.example.storage.ParquetFilesReader;
import org.apache.avro.generic.GenericRecord;
import java.io.File;

public class WeatherIndexer {

    private final ElasticsearchClient client;

    public WeatherIndexer(ElasticsearchClient client) {
        this.client = client;
    }

    public void indexParquetFile(File parquetFile) throws Exception {

        ParquetFilesReader.readParquet(
                parquetFile.getAbsolutePath(),
                record -> {

                    try {
//                        System.out.println(record.toString());
                        WeatherRecord wr = mapToWeatherRecord(record);

                        client.index(i -> i
                                .index("weather-records")
                                .document(wr)
                        );

                    } catch (Exception e) {
                        System.err.println(e);
                        System.exit(-1);
                    }
                }
        );
    }

    private WeatherRecord mapToWeatherRecord(GenericRecord record) {
        long stationId = toLong(record.get("station_id"));
        long sNo = toLong(record.get("s_no"));
        String batteryStatus = record.get("battery_status") != null
                ? record.get("battery_status").toString().toUpperCase()
                : "LOW";

        long timestamp = toLong(record.get("status_timestamp"));
        int humidity = toInt(record.get("humidity"));
        int temperature = toInt(record.get("temperature"));
        int windSpeed = toInt(record.get("wind_speed"));

        return new WeatherRecord(
                stationId,
                sNo,
                BatteryStatus.valueOf(batteryStatus),
                timestamp,
                humidity,
                temperature,
                windSpeed
        );
    }

    // ---------- safe converters ----------

    private long toLong(Object value) {
        if (value == null) return 0L;
        return Long.parseLong(value.toString());
    }

    private int toInt(Object value) {
        if (value == null) return 0;
        return Integer.parseInt(value.toString());
    }
}