package com.example;

import com.example.consumer.WeatherKafkaConsumer;
import com.example.storage.WeatherStorageCoordinator;
import com.example.storage.bitcask.BitCaskStore;
import com.example.storage.parquet.ParquetArchiver;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class CentralStationApp {

    public static void main(String[] args) throws Exception {
        String bitcaskDir = System.getenv()
                .getOrDefault("BITCASK_DIR", "data/bitcask");

        BitCaskStore bitCask = new BitCaskStore(bitcaskDir);
        ParquetArchiver parquet = new ParquetArchiver();
        WeatherStorageCoordinator coordinator = new WeatherStorageCoordinator(bitCask, parquet);

        WeatherKafkaConsumer consumer = new WeatherKafkaConsumer(coordinator);

        // Schedule periodic compaction
        int compactionInterval = Integer.parseInt(System.getenv().getOrDefault("COMPACTION_INTERVAL_MINS", "1"));
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                System.out.println("[Scheduled Compaction] Starting background compaction...");
                bitCask.compact();
                System.out.println("[Scheduled Compaction] Background compaction completed successfully.");
            } catch (Exception e) {
                System.err.println("[Scheduled Compaction] Error during background compaction: " + e.getMessage());
                e.printStackTrace();
            }
        }, compactionInterval, compactionInterval, TimeUnit.MINUTES);

        // Start HTTP Server on port 8080
        startHttpServer(bitCask);

        // Add shutdown hook to close resources
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down Central Station...");
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
                bitCask.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));

        // Runs forever on the main thread — Ctrl+C to stop
        consumer.run();
    }

    private static void startHttpServer(BitCaskStore bitCask) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/bitcask/all", (exchange) -> {
            List<String> keys = bitCask.listKeys();
            StringBuilder sb = new StringBuilder();
            sb.append("key,value\n");
            for (String key : keys) {
                byte[] val = bitCask.get(key);
                if (val != null) {
                    sb.append(key).append(",\"").append(new String(val).replace("\"", "\"\"")).append("\"\n");
                }
            }
            sendResponse(exchange, sb.toString());
        });

        server.createContext("/bitcask/get", (exchange) -> {
            String query = exchange.getRequestURI().getQuery();
            String key = null;
            if (query != null && query.startsWith("key=")) {
                key = query.substring(4);
            }

            if (key == null) {
                sendResponse(exchange, "Missing key parameter", 400);
                return;
            }

            byte[] val = bitCask.get(key);
            if (val == null) {
                sendResponse(exchange, "Key not found", 404);
            } else {
                sendResponse(exchange, new String(val));
            }
        });

        server.createContext("/bitcask/compact", (exchange) -> {
            try {
                bitCask.compact();
                sendResponse(exchange, "Compaction completed successfully.");
            } catch (Exception e) {
                sendResponse(exchange, "Compaction failed: " + e.getMessage(), 500);
            }
        });

        server.setExecutor(null); // creates a default executor
        server.start();
        System.out.println("[HTTP Server] started on port 8080");
    }

    private static void sendResponse(HttpExchange exchange, String response) throws IOException {
        sendResponse(exchange, response, 200);
    }

    private static void sendResponse(HttpExchange exchange, String response, int code) throws IOException {
        byte[] bytes = response.getBytes();
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}