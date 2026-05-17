package com.example;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.example.config.ElasticConfig;
import io.github.cdimascio.dotenv.Dotenv;
import java.io.File;


public class ElasticSearchClientApp {


    public static void main(String[] args)
            throws Exception {

        Dotenv dotenv = Dotenv.load();

        ElasticsearchClient client =
                ElasticConfig.getClient();

        WeatherIndexer weatherIndexer =
                new WeatherIndexer(client);

        File root =
                new File(dotenv.get("PARQUET_ROOT_DIR"));

        ParquetWatcherService watcher =
                new ParquetWatcherService(
                        root.toPath(),
                        weatherIndexer
                );


        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                System.out.println("Shutting down application...");
                watcher.stop();
                ElasticConfig.close();
                System.out.println("Application shutdown complete.");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }));
        try{
            watcher.start();
        } catch (Exception e) {
            System.exit(1);
        }
    }
}