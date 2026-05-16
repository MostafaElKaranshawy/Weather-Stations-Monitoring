package com.example;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.example.config.ElasticConfig;

import java.io.File;

public class ElasticSearchClientApp {

    public static void main(String[] args)
            throws Exception {

        ElasticsearchClient client =
                ElasticConfig.getClient();

        WeatherIndexer weatherIndexer =
                new WeatherIndexer(client);

        File root =
                new File("./data/parquet");

        ParquetWatcherService watcher =
                new ParquetWatcherService(
                        root.toPath(),
                        weatherIndexer
                );

        watcher.start();
    }
}