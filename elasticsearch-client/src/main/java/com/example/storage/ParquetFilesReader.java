package com.example.storage;

import org.apache.avro.generic.GenericRecord;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;

import org.apache.parquet.avro.AvroParquetReader;
import org.apache.parquet.hadoop.ParquetReader;

import java.util.function.Consumer;

public class ParquetFilesReader {

        public static void readParquet(String parquetFile,
                                       Consumer<GenericRecord> consumer)
                throws Exception {

            Path path = new Path(parquetFile);

            ParquetReader<GenericRecord> reader =
                    AvroParquetReader
                            .<GenericRecord>builder(path)
                            .withConf(new Configuration())
                            .build();

            GenericRecord record;

            while ((record = reader.read()) != null) {
                consumer.accept(record);
            }

            reader.close();
        }
    }