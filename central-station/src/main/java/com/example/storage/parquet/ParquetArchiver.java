package com.example.storage.parquet;

import com.example.model.WeatherRecord;
import io.github.cdimascio.dotenv.Dotenv;
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
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ParquetArchiver implements AutoCloseable {

    static Dotenv dotenv = Dotenv.load();
    private static final int BATCH_SIZE = Integer.parseInt(dotenv.get("BATCH_SIZE"));
    private static final String BASE_DIR =
            System.getenv().getOrDefault("PARQUET_BASE_DIR", dotenv.get("PARQUET_BASE_DIR"));

    private final Schema schema;
    private final List<Future<?>> pendingWrites = new ArrayList<>();

    // thread pool
    private final ThreadPoolExecutor writersPool = new ThreadPoolExecutor(
            Math.min(10, Runtime.getRuntime().availableProcessors()),
            Runtime.getRuntime().availableProcessors(),
            60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(100),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    // buffer to keep records before writing to disk, keyed by stationId
    private List<WeatherRecord> buffer = new ArrayList<>(BATCH_SIZE);

    public ParquetArchiver() {
        this.schema = loadSchema();
    }

    public void archive(WeatherRecord weatherRecord) throws IOException {
        this.buffer.add(weatherRecord);

        if (this.buffer.size() >= BATCH_SIZE)
            persistParquetToDisk();
    }

    private void persistParquetToDisk() {
        pendingWrites.removeIf(Future::isDone);
        // local version of the current batch
        List<WeatherRecord> batch = this.buffer;
        this.buffer = new ArrayList<>(BATCH_SIZE);
        Map<Long, List<WeatherRecord>> recordsByStation = batch.stream()
                .collect(Collectors.groupingBy(WeatherRecord::getStationId));

        // submit write tasks per batch
        for (var entry : recordsByStation.entrySet()) {
            long stationId = entry.getKey();
            List<WeatherRecord> stationRecords = entry.getValue();

            Future<?> future = writersPool.submit(() -> {
                try {
                    writeStationBatch(stationId, stationRecords);
                } catch (IOException e) {
                    System.err.println("Failed to write Parquet for station " + stationId + ": " + e.getMessage());
                }
            });

            pendingWrites.add(future);
        }
    }

    // Directory: /archived-data/parquet/date=YYYY-MM-DD/station=N/
    // Filename:  part-{currentTimeMillis}.parquet
    private void writeStationBatch(long stationId, List<WeatherRecord> stationRecords) throws IOException{
        String date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String dir = BASE_DIR + "/date=" + date + "/station=" + stationId;

        new File(dir).mkdirs();

        String file = dir + "/part-" + System.currentTimeMillis() + ".parquet";

        try (ParquetWriter<GenericRecord> writer =
                     AvroParquetWriter.<GenericRecord>builder(new Path(file))
                             .withSchema(schema)
                             .withConf(new Configuration())
                             .withCompressionCodec(CompressionCodecName.SNAPPY)
                             .build()) {

            for (WeatherRecord r : stationRecords) {
                writer.write(createGenericRecord(r));
            }
        }

        System.out.printf("[Parquet] station=%d written=%d file=%s%n",
                stationId, stationRecords.size(), file);
    }

    @Override
    public void close() throws Exception {
        System.out.println("[Parquet] persisting remaining buffers on shutdown...");

        if (!buffer.isEmpty())
            persistParquetToDisk();

        for (Future<?> future : pendingWrites)
            future.get();

        writersPool.shutdown();
        if (!writersPool.awaitTermination(60, TimeUnit.SECONDS)) {
            System.err.println("[Parquet] Warning: some write tasks did not finish in time");
            writersPool.shutdownNow();
        }
        else
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

