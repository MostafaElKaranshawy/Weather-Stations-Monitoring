package com.example;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.example.config.ElasticConfig;

import java.io.File;
import java.util.Arrays;

public class ElasticSearchClient {
    public static void main(String[] args) {
        ElasticsearchClient client = ElasticConfig.getClient();

        WeatherIndexer indexer = new WeatherIndexer(client);

        File root = new File("/home/moka/data/second term/Data Intensive/Assignments/Weather-Stations-Monitoring/data/parquet");

        if (!root.exists() || !root.isDirectory()) {
            throw new IllegalArgumentException("Invalid root directory");
        }

        File[] dateFolders = root.listFiles();
        if (dateFolders == null) return;

        for (File dateFolder : dateFolders) {

            if (!dateFolder.isDirectory()) continue;

            File[] stationFolders = dateFolder.listFiles();
            if (stationFolders == null) continue;

            for (File stationFolder : stationFolders) {

                if (!stationFolder.isDirectory()) continue;

                File[] parquetFiles = stationFolder.listFiles(
                        (dir, name) -> name.endsWith(".parquet")
                );

                if (parquetFiles == null) continue;

                Arrays.stream(parquetFiles).forEach(file -> {
                    try {
                        indexer.indexParquetFile(file);
                    } catch (Exception e) {
                        System.err.println("Failed file: " + file.getName());
                        e.printStackTrace();
                    }
                });
            }
        }

        System.out.println("Indexing completed.");
    }
}