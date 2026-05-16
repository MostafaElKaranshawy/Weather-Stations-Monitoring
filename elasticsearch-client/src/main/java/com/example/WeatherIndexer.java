package com.example;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;

import com.example.model.BatteryStatus;
import com.example.model.WeatherRecord;
import com.example.storage.ParquetFilesReader;

import org.apache.avro.generic.GenericRecord;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class WeatherIndexer {

    private final ElasticsearchClient client;

    private static final int BATCH_SIZE = 1000;

    public WeatherIndexer(ElasticsearchClient client) {
        this.client = client;
    }

    public void indexParquetFile(File parquetFile) throws Exception {

        System.out.println("Indexing: " + parquetFile);

        List<WeatherRecord> batch = new ArrayList<>();

        ParquetFilesReader.readParquet(
                parquetFile.getAbsolutePath(),
                record -> {
                    try {
                        WeatherRecord wr = mapToWeatherRecord(record);
                        System.out.println(record);
                        batch.add(wr);
                        if (batch.size() >= BATCH_SIZE) {
                            bulkIndex(batch);
                            batch.clear();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
        );

        if (!batch.isEmpty()) {
            bulkIndex(batch);
        }
    }

    private void bulkIndex(List<WeatherRecord> records) throws Exception {

        BulkRequest.Builder br = new BulkRequest.Builder();

        for (WeatherRecord wr : records) {
            String docId = wr.getStationId() + "_" + wr.getSNo();
            br.operations(op -> op
                    .index(idx -> idx
                            .index("weather-records")
                            .id(docId)
                            .document(wr)
                    )
            );
        }

        BulkResponse response = client.bulk(br.build());

        if (response.errors()) {
            System.err.println("Bulk indexing errors:");
            response.items().forEach(item -> {
                if (item.error() != null) {
                    System.err.println(item.error().reason());
                }
            });
        }
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

    private long toLong(Object value) {
        if (value == null) return 0L;
        return Long.parseLong(value.toString());
    }

    private int toInt(Object value) {
        if (value == null) return 0;
        return Integer.parseInt(value.toString());
    }
}