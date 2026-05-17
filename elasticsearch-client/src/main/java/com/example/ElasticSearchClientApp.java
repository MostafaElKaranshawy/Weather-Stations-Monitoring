package com.example;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.example.config.ElasticConfig;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.File;

public class ElasticSearchClientApp {

    public static void main(String[] args) {

        ParquetWatcherService watcher = null;

        try {
            Dotenv dotenv = Dotenv.load();

            ElasticsearchClient client = ElasticConfig.getClient();

            WeatherIndexer weatherIndexer = new WeatherIndexer(client);

            // Parquet files path
            File root = new File(dotenv.get("PARQUET_ROOT_DIR"));

            // Base folder not created yet by the central station, create it now.
            if (!root.exists()) {
                boolean created = root.mkdirs();
                if (!created) {
                    throw new RuntimeException(
                            "Failed to create root directory: " + root.getAbsolutePath()
                    );
                }
            }

            // Files watcher to detect new files creation
            watcher = new ParquetWatcherService(root.toPath(), weatherIndexer);

            final ParquetWatcherService finalWatcher = watcher;

            // Shut Down Hook for gracefully exiting.
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down application...");
                try {

                    // Close the watcher service.
                    finalWatcher.stop();
                } catch (Exception e) {
                    System.err.println("Error while stopping watcher: " + e.getMessage());
                }
                System.out.println("Application shutdown complete.");
            }));

            // Start the watcher process (monitoring files)
            watcher.start();

            // stop watcher process (only returned here if the application has been interrupted).
            watcher.stop();

        } catch (Exception e) {
            System.err.println("Application failed: " + e.getMessage());
            e.printStackTrace();

            try {
                if (watcher != null) {
                    watcher.stop();
                }
            } catch (Exception ex) {
                System.err.println("Failed to stop watcher: " + ex.getMessage());
            }
        }
    }
}