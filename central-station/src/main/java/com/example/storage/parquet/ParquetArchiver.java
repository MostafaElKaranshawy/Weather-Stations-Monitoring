package com.example.storage.parquet;

import com.example.model.WeatherRecord;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class ParquetArchiver implements AutoCloseable {

    private static final int BATCH_SIZE = 100;
    private static final String BASE_DIR = "./data/parquet";

    private final Schema schema;

    // check if concurrentHashMap is actually needed here or not
    private final ConcurrentHashMap<Long, List<WeatherRecord>> buffers =
            new ConcurrentHashMap<>();

    public ParquetArchiver() {
        this.schema = loadSchema();
    }

    public void archive(WeatherRecord weatherRecord) throws IOException {
        List<WeatherRecord> buffer = buffers.computeIfAbsent(
                weatherRecord.getStationId(), k -> new java.util.ArrayList<>()
        );
        buffer.add(weatherRecord);

        if (buffer.size() >= BATCH_SIZE) {
            persistParquetToDisk(weatherRecord.getStationId());
        }
    }

    // Directory: /archived-data/parquet/date=YYYY-MM-DD/station=N/
    // Filename:  part-{currentTimeMillis}.parquet
    private void persistParquetToDisk(long stationId) throws IOException{
        List<WeatherRecord> buffer = buffers.get(stationId);
        if (buffer == null || buffer.isEmpty())
            return;

        // returns the old value associated with the specified key
        // and clears the buffer for the station
        List<WeatherRecord> toWrite = buffers.put(stationId, new ArrayList<>());

        // Build directory path using Hive partition naming convention
        String date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        // CHECK THIS PARTITIONING STRATEGY
        String dirPath = BASE_DIR + "/date=" + date + "/station=" + stationId;
        new File(dirPath).mkdirs(); // create if it doesn't exist yet

        String filePath = dirPath + "/part-" + System.currentTimeMillis() + ".parquet";

        Configuration hadoopConf = new Configuration();

        try (ParquetWriter<GenericRecord> writer =
                     AvroParquetWriter.<GenericRecord>builder(new Path(filePath))
                             .withSchema(schema)
                             .withConf(hadoopConf)
                             // SNAPPY compression: good balance of speed and file size.
                             // Alternative: GZIP for smaller files, UNCOMPRESSED for max speed.
                             .withCompressionCodec(CompressionCodecName.SNAPPY)
                             .build()) {

            for (WeatherRecord weatherRecord : toWrite) {
                GenericRecord row = createGenericRecord(weatherRecord);
                writer.write(row);
            }
        }

        System.out.printf("[Parquet] persisted %d records for station %d → %s%n",
                toWrite.size(), stationId, filePath);
    }

    @Override
    public void close() throws Exception {
        System.out.println("[Parquet] persisting remaining buffers on shutdown...");

        for (var entry : buffers.entrySet()) {
            long stationId = entry.getKey();
            persistParquetToDisk(stationId);
        }

        System.out.println("[Parquet] all buffers persisted to disk");
    }

    private Schema loadSchema() {
        try (InputStream inputStream = getClass().getResourceAsStream("/avro/schema.avsc")) {
            if (inputStream == null)
                throw new RuntimeException("Schema file not found");

            return new Schema.Parser().parse(inputStream);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load Avro schema", e);
        }
    }

    private GenericRecord createGenericRecord(WeatherRecord weatherRecord) {
        GenericRecord row = new GenericData.Record(schema);
        row.put("station_id", weatherRecord.getStationId());
        row.put("s_no", weatherRecord.getSNo());
        row.put("battery_status", weatherRecord.getBatteryStatus().name().toLowerCase());
        row.put("status_timestamp", weatherRecord.getTimestamp());
        row.put("humidity", weatherRecord.getHumidity());
        row.put("temperature", weatherRecord.getTemperature());
        row.put("wind_speed", weatherRecord.getWindSpeed());
        return row;
    }

}

