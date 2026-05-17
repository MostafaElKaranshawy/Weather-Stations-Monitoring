package com.example;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;

import com.example.model.BatteryStatus;
import com.example.model.WeatherRecord;
import com.example.storage.ParquetFilesReader;

import io.github.cdimascio.dotenv.Dotenv;
import org.apache.avro.generic.GenericRecord;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

public class WeatherIndexer {
    static Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
    private final ElasticsearchClient client;

    private static final int BATCH_SIZE = Integer.parseInt(dotenv.get("INDEXING_BATCH_SIZE") != null ? dotenv.get("INDEXING_BATCH_SIZE") : System.getenv().getOrDefault("INDEXING_BATCH_SIZE", ""));
    private static final String INDEX_NAME = dotenv.get("ELASTICSEARCH_INDEX_NAME") != null ? dotenv.get("ELASTICSEARCH_INDEX_NAME") : System.getenv().getOrDefault("ELASTICSEARCH_INDEX_NAME", "");

    // Client Injection.
    public WeatherIndexer(ElasticsearchClient client) {
        this.client = client;
    }

    // Given parquet file, read its records, map to WeatherRecord, and bulk index to Elasticsearch.
    public void indexParquetFile(File parquetFile) throws Exception {
        List<WeatherRecord> batch = new ArrayList<>();
        try {
            ParquetFilesReader.readParquet(
                    parquetFile.getAbsolutePath(),
                    record -> {
                        try {
                            WeatherRecord wr = mapToWeatherRecord(record);
                            batch.add(wr);
                            if (batch.size() >= BATCH_SIZE) {
                                bulkIndex(batch);
                                batch.clear();
                            }
                        } catch (Exception e) {
                            throw new RuntimeException(
                                    "Error mapping record to WeatherRecord: " + e.getMessage()
                            );
                        }
                    }
            );
            // File ends and there may be remaining records in batch, so index them as well.
            if (!batch.isEmpty()) {
                bulkIndex(batch);
            }
        } catch (FileNotFoundException e) {
            throw new FileNotFoundException(
                    "Parquet file not found: " + parquetFile.getAbsolutePath()
            );
        } catch (Exception e) {
            throw new RuntimeException(
                    "Error processing file: " + parquetFile.getAbsolutePath() + " - " + e.getMessage()
            );
        }
    }

    // Index records in batches for better performance (lower latency)
    private void bulkIndex(List<WeatherRecord> records) throws Exception {
        BulkRequest.Builder br = new BulkRequest.Builder();

        for (WeatherRecord wr : records) {

            // For each station the sequence number is unique, so our key here is the `stationId_seqNo`
            String docId = wr.getStationId() + "_" + wr.getSNo();
            br.operations(op -> op
                    .index(idx -> idx
                            .index(INDEX_NAME)
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

    // Mapping read records from parquet file to WeatherRecord class.
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

    // Shutdown the service and release resources (like Elasticsearch client connections).
    public void shutdown() {
        System.out.println("[Shutdown] shutting down Elasticsearch Client");
        this.client.shutdown();
        System.out.println("[Shutdown] Elasticsearch Client shutdown complete");
    }
}